"""
crowd_detector.py — YOLOv8n person detection, heatmap generation, occupancy calculation.

Windows compatibility notes:
- All numpy operations use CPU arrays; no GPU tensor indexing.
- box.xyxy[0].cpu().tolist() ensures tensors are moved to CPU before conversion,
  which is required when running on a CUDA GPU (common on Windows gaming PCs)
  and avoids RuntimeError: "Can't convert CUDA tensor to numpy".

Detection accuracy tuning:
- CONFIDENCE_THRESHOLD / NMS_IOU_THRESHOLD are passed straight to the YOLO
  call (model(..., conf=..., iou=...)) instead of relying on Ultralytics'
  defaults (conf=0.25, iou=0.7). The confidence floor is tuned to cut false
  positives (background clutter, blurry shapes) without discarding
  partially-visible/occluded people, while the IoU threshold makes NMS
  collapse near-duplicate boxes YOLO sometimes emits for a single person.
- MIN_BOX_AREA_RATIO drops degenerate boxes that are implausibly small
  relative to the frame (typically noise, not a real person), further
  cutting false positives without touching genuinely small/far-away people.
- _dedupe_boxes() runs a second, containment-based pass AFTER YOLO's own
  NMS: in crowded/overlapping scenes it's common for one person to produce
  a large box and a smaller box mostly contained within it (e.g. an arm or
  torso re-detected). Standard IoU-based NMS can miss these because IoU is
  low when the boxes are very different sizes even though one is almost
  entirely inside the other. This directly reduces double-counting from a
  single detection pass, on top of YOLO's own NMS.

Counting policy (important): every call to detect_people() analyses ONE
frame independently and returns the count of people visible in THAT frame
only. There is no cross-frame memory/tracking here by design — the caller
is responsible for always using the most recent frame's count as "the
displayed count" (see analyze.py), never an average or history across
frames.
"""

import base64
import logging

import cv2
import numpy as np

from utils.model_loader import ModelLoader

logger = logging.getLogger(__name__)

# YOLOv8 COCO dataset: class 0 = person
PERSON_CLASS_ID = 0

# Minimum detection confidence to keep a box. Tuned down slightly from an
# overly strict floor so partially-visible/occluded people (lower, more
# uncertain confidence) are still detected, while still well above
# Ultralytics' default (0.25) to reject background-clutter false positives.
CONFIDENCE_THRESHOLD = 0.30
# NMS IoU threshold. Lowered from the Ultralytics default (0.7) so
# overlapping boxes on the SAME person are collapsed more aggressively,
# reducing duplicate/near-duplicate bounding boxes — while still high
# enough (0.45) to keep genuinely distinct, closely-standing people apart.
NMS_IOU_THRESHOLD = 0.45
# Boxes smaller than this fraction of the frame area are treated as noise
# and dropped (helps with false positives on low-quality/compressed streams).
MIN_BOX_AREA_RATIO = 0.0004
# For the containment-based dedup pass: if this fraction (or more) of a
# smaller box's area sits inside a larger box, treat it as a duplicate
# detection of the same person rather than two different people.
CONTAINMENT_OVERLAP_RATIO = 0.75


def detect_people(image_bytes: bytes) -> dict:
    """
    Run YOLOv8n on raw JPEG/PNG bytes.

    Returns:
        dict with keys:
          people_count  (int)  — count of people visible in THIS frame only
          frame_b64     (str | None) — annotated frame as base64 JPEG
          heatmap_b64   (str | None) — density heatmap as base64 JPEG
    """
    if not image_bytes:
        return {"people_count": 0, "frame_b64": None, "heatmap_b64": None}

    try:
        model = ModelLoader.get_model()
    except RuntimeError as exc:
        logger.error("Model unavailable: %s", exc)
        return {"people_count": 0, "frame_b64": None, "heatmap_b64": None}

    # Decode image — works with JPEG, PNG, BMP on all platforms
    nparr = np.frombuffer(image_bytes, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    if frame is None:
        logger.warning("cv2.imdecode returned None — image bytes may be corrupt")
        return {"people_count": 0, "frame_b64": None, "heatmap_b64": None}

    frame_area = frame.shape[0] * frame.shape[1]
    min_box_area = frame_area * MIN_BOX_AREA_RATIO

    try:
        results = model(
            frame,
            classes=[PERSON_CLASS_ID],
            conf=CONFIDENCE_THRESHOLD,
            iou=NMS_IOU_THRESHOLD,
            verbose=False,
        )[0]
    except Exception as exc:
        logger.error("YOLO inference failed: %s", exc)
        return {"people_count": 0, "frame_b64": None, "heatmap_b64": None}

    boxes = results.boxes

    # Collect surviving boxes (post-YOLO-NMS, pre-area-filter) as
    # (x1, y1, x2, y2, confidence) so they can be sorted/deduped below.
    candidates = []
    if boxes is not None and len(boxes) > 0:
        for box in boxes:
            # .cpu() is required when CUDA is available — safe no-op on CPU-only systems
            x1, y1, x2, y2 = map(int, box.xyxy[0].cpu().tolist())
            conf = float(box.conf[0].cpu())

            # Drop implausibly small/degenerate boxes — usually noise, not a
            # real person, and a common source of false positives.
            box_area = max(x2 - x1, 0) * max(y2 - y1, 0)
            if box_area < min_box_area:
                continue

            candidates.append((x1, y1, x2, y2, conf))

    # Second dedup pass for crowded/overlapping people: removes boxes that
    # are mostly CONTAINED within a higher-confidence box, catching
    # duplicate detections of the same person that plain IoU-based NMS
    # (which compares union vs. intersection, not containment) can miss.
    extracted_boxes, confidences = _dedupe_boxes(candidates)

    # Draw bounding boxes on a copy of the frame
    annotated = frame.copy()
    for (x1, y1, x2, y2), conf in zip(extracted_boxes, confidences):
        cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 255, 0), 2)
        label_y = max(y1 - 5, 15)
        cv2.putText(
            annotated,
            f"Person {conf:.2f}",
            (x1, label_y),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.45,
            (0, 255, 0),
            1,
            cv2.LINE_AA,
        )

    # Every person currently visible in THIS frame, counted exactly once.
    people_count = len(extracted_boxes)

    # People count overlay
    cv2.putText(
        annotated,
        f"People: {people_count}",
        (10, 30),
        cv2.FONT_HERSHEY_SIMPLEX,
        1.0,
        (0, 0, 255),
        2,
        cv2.LINE_AA,
    )

    heatmap = _generate_heatmap(frame, extracted_boxes)

    return {
        "people_count": people_count,
        "frame_b64": _encode_image(annotated),
        "heatmap_b64": _encode_image(heatmap),
    }


def _dedupe_boxes(candidates: list) -> tuple:
    """
    Remove duplicate person detections that YOLO's own NMS can miss.

    `candidates` is a list of (x1, y1, x2, y2, confidence). Boxes are
    processed highest-confidence-first; a lower-confidence box is dropped
    if CONTAINMENT_OVERLAP_RATIO or more of ITS OWN area overlaps with a
    box already kept — this catches one person producing two boxes of very
    different sizes (e.g. a tight torso box and a looser full-body box),
    which standard IoU-based NMS can fail to merge since IoU is diluted by
    the size difference even when one box sits almost entirely inside the
    other.

    Returns (kept_boxes, kept_confidences) as parallel lists so each box
    can still be labelled with its own confidence.
    """
    if not candidates:
        return [], []

    ordered = sorted(candidates, key=lambda b: b[4], reverse=True)
    kept: list = []
    kept_conf: list = []

    for x1, y1, x2, y2, conf in ordered:
        area = max(x2 - x1, 0) * max(y2 - y1, 0)
        if area == 0:
            continue

        is_duplicate = False
        for kx1, ky1, kx2, ky2 in kept:
            ix1, iy1 = max(x1, kx1), max(y1, ky1)
            ix2, iy2 = min(x2, kx2), min(y2, ky2)
            inter_w, inter_h = max(ix2 - ix1, 0), max(iy2 - iy1, 0)
            inter_area = inter_w * inter_h
            if inter_area / area >= CONTAINMENT_OVERLAP_RATIO:
                is_duplicate = True
                break

        if not is_duplicate:
            kept.append((x1, y1, x2, y2))
            kept_conf.append(conf)

    return kept, kept_conf


def _generate_heatmap(frame: np.ndarray, boxes: list) -> np.ndarray:
    """
    Build a Gaussian density heatmap blended over the original frame.

    Each detected person contributes a 2-D Gaussian blob centred on their
    bounding-box midpoint; the result is colour-mapped with COLORMAP_JET and
    alpha-blended onto the source frame.
    """
    h, w = frame.shape[:2]
    heat = np.zeros((h, w), dtype=np.float32)

    for x1, y1, x2, y2 in boxes:
        cx = (x1 + x2) // 2
        cy = (y1 + y2) // 2
        sigma_x = max((x2 - x1) // 2, 10)
        sigma_y = max((y2 - y1) // 2, 10)

        # Vectorised Gaussian — much faster than per-pixel loop
        y_coords, x_coords = np.ogrid[:h, :w]
        gaussian = np.exp(
            -(
                ((x_coords - cx) ** 2) / (2.0 * sigma_x ** 2)
                + ((y_coords - cy) ** 2) / (2.0 * sigma_y ** 2)
            )
        ).astype(np.float32)
        heat += gaussian

    # Normalise to 0-255
    if heat.max() > 0:
        heat = (heat / heat.max() * 255.0).clip(0, 255).astype(np.uint8)
    else:
        heat = heat.astype(np.uint8)

    heatmap_color = cv2.applyColorMap(heat, cv2.COLORMAP_JET)
    return cv2.addWeighted(frame, 0.5, heatmap_color, 0.5, 0)


def _encode_image(frame: np.ndarray) -> str:
    """Encode a BGR ndarray as a base64 JPEG string (quality 80)."""
    ok, buffer = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
    if not ok:
        raise RuntimeError("cv2.imencode failed")
    return base64.b64encode(buffer).decode("utf-8")


def calculate_occupancy(people_count: int, max_capacity: int) -> dict:
    """
    Calculate occupancy percentage and crowd level label.

    Formula: occupancy = (people_count / max_capacity) * 100

    Levels (matching backend CrowdData.CrowdLevel enum):
        0  – 25  % -> LOW
        26 – 50  % -> MEDIUM
        51 – 75  % -> HIGH
        76 – 100 % -> CRITICAL
        > 100    % -> OVERCROWDED
    """
    if max_capacity <= 0:
        max_capacity = 1

    occupancy = round((people_count / max_capacity) * 100.0, 2)

    if occupancy > 100:
        level = "OVERCROWDED"
    elif occupancy > 75:
        level = "CRITICAL"
    elif occupancy > 50:
        level = "HIGH"
    elif occupancy > 25:
        level = "MEDIUM"
    else:
        level = "LOW"

    return {
        "people_count": people_count,
        "max_capacity": max_capacity,
        "occupancy_percentage": occupancy,
        "crowd_level": level,
    }
