"""
analyze.py — FastAPI router for crowd density analysis endpoints.

Endpoints:
  POST /api/analyze/image   — analyze a single JPEG/PNG frame
  POST /api/analyze/video   — upload a video for background processing
  POST /api/analyze/stream  — analyze frames from a live RTSP/HTTP stream
  GET  /api/analyze/validate-stream — validate a stream URL is reachable

Windows compatibility fixes:
- /tmp/ replaced with tempfile.mkstemp() which uses %TEMP% on Windows
- cv2.VideoCapture timeout set via CAP_PROP_OPEN_TIMEOUT_MSEC (avoids hanging)
- All file handles explicitly closed before os.unlink() (Windows locks open files)
- URL encoding applied to streamUrl path parameter

Parameter naming:
- camera_id  (snake_case) used consistently in all form fields to avoid
  cameraId / camera_id confusion that caused 422 Unprocessable Entity errors.
- The backend Spring Boot endpoint receives peopleCount as a query param
  which matches @RequestParam int peopleCount in MonitoringController.java.
"""

import asyncio
import base64
import logging
import os
import tempfile
import threading
import time
from urllib.parse import urlparse

import cv2
import httpx
import numpy as np
import requests
from fastapi import APIRouter, BackgroundTasks, File, Form, HTTPException, Query, UploadFile

from utils.crowd_detector import calculate_occupancy, detect_people

router = APIRouter()
logger = logging.getLogger(__name__)

BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")

# Maximum frames to sample per video background job (prevents OOM on large files)
MAX_VIDEO_FRAMES = 500
# Number of frames to capture per stream analysis call / per continuous-session batch
STREAM_FRAMES_DEFAULT = 5
# OpenCV stream open timeout in milliseconds (important on Windows — default is infinite)
STREAM_OPEN_TIMEOUT_MS = 10_000
# How many times to retry a failed frame read from a stream
STREAM_READ_RETRIES = 3
# Pause between frame batches within a continuous session (seconds). The camera
# stays open across batches — this is just a small breather between them.
STREAM_BATCH_INTERVAL_SEC = 1.0
# When a batch fails to read any frames (stream ended prematurely / dead
# handle), how long to wait before closing + reopening the capture, and
# between each reopen retry. Recovery keeps retrying indefinitely (still on
# the SAME analysis session) until Stop Analysis is pressed.
STREAM_RECOVERY_WAIT_SEC = 2.0


# ---------------------------------------------------------------------------
# Continuous stream session manager
# ---------------------------------------------------------------------------
#
# Problem this solves: the frontend used to call POST /api/analyze/stream
# every few seconds, and every single call opened a brand new
# cv2.VideoCapture, read a handful of frames, and closed it again. That
# meant the camera was being reopened continuously, which is slow, can
# fail intermittently on RTSP sources, and produced a flood of duplicate
# crowd-data pushes (hence duplicate notifications).
#
# The fix: one long-lived background thread PER CAMERA that opens the
# VideoCapture exactly once and keeps reading frames in batches until
# explicitly stopped, at which point (and only then) the capture is
# released. "Analyze Now" now just starts (or no-ops into) this session;
# "Stop Camera Service" stops it.
class StreamSession:
    def __init__(self, camera_id: int, stream_url: str, capacity: int, frames_per_batch: int):
        self.camera_id = camera_id
        self.stream_url = stream_url
        self.capacity = capacity
        self.frames_per_batch = frames_per_batch
        self.stop_event = threading.Event()
        self.thread: "threading.Thread | None" = None
        self.cap: "cv2.VideoCapture | None" = None
        self.started_at = time.time()

    def is_alive(self) -> bool:
        return self.thread is not None and self.thread.is_alive()


# camera_id -> StreamSession, one entry max per camera ("one active analysis
# task per camera").
_sessions: dict[int, StreamSession] = {}
_sessions_lock = threading.Lock()


# ---------------------------------------------------------------------------
# Image analysis
# ---------------------------------------------------------------------------

@router.post("/image")
async def analyze_image(
    file: UploadFile = File(...),
    camera_id: int = Form(...),
    capacity: int = Form(100),
):
    """Analyze a single uploaded image frame for crowd density."""
    try:
        image_bytes = await file.read()
        if not image_bytes:
            raise HTTPException(status_code=400, detail="Uploaded file is empty")

        detection = detect_people(image_bytes)
        occupancy = calculate_occupancy(detection["people_count"], capacity)
        await _push_to_backend(camera_id, detection, occupancy)

        return {
            "success": True,
            "camera_id": camera_id,
            **occupancy,
            "frame_b64": detection["frame_b64"],
            "heatmap_b64": detection["heatmap_b64"],
        }
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Image analysis failed")
        raise HTTPException(status_code=500, detail=str(exc))


# ---------------------------------------------------------------------------
# Video upload — background processing
# ---------------------------------------------------------------------------

@router.post("/video")
async def analyze_video(
    background_tasks: BackgroundTasks,
    video: UploadFile = File(...),
    # Use snake_case consistently; frontend sends camera_id, cameraId also accepted
    camera_id: str = Form(...),
    capacity: str = Form("100"),
):
    """
    Accept an uploaded video file and process it asynchronously.

    The video is saved to a system temp directory (Windows-safe) and analysed
    frame-by-frame in a background task. Results are pushed to the backend as
    they are produced so the dashboard updates in near-real-time.
    """
    video_bytes = await video.read()
    if not video_bytes:
        raise HTTPException(status_code=400, detail="Uploaded video file is empty")

    # Sanitise filename — avoid path traversal on Windows
    original_name = video.filename or f"upload_{camera_id}.mp4"
    safe_name = os.path.basename(original_name).replace(" ", "_")

    background_tasks.add_task(
        _process_video_background,
        video_bytes,
        int(camera_id),
        int(capacity),
        safe_name,
    )
    return {
        "success": True,
        "message": "Video analysis started in background",
        "camera_id": int(camera_id),
        "filename": safe_name,
    }


async def _process_video_background(
    video_bytes: bytes,
    camera_id: int,
    capacity: int,
    filename: str,
) -> None:
    """
    Write video to a temp file, sample frames, push results to backend.

    Uses tempfile.mkstemp() which resolves to %TEMP% on Windows and /tmp on Linux.
    The file descriptor is closed immediately after writing so cv2.VideoCapture
    can open it on Windows (which locks files held by open handles).
    """
    tmp_fd = None
    tmp_path = None
    try:
        # mkstemp returns (fd, path); we close fd right after writing
        suffix = os.path.splitext(filename)[1] or ".mp4"
        tmp_fd, tmp_path = tempfile.mkstemp(suffix=suffix, prefix=f"crowd_{camera_id}_")
        try:
            os.write(tmp_fd, video_bytes)
        finally:
            os.close(tmp_fd)  # MUST close before VideoCapture on Windows
            tmp_fd = None

        cap = cv2.VideoCapture(tmp_path)
        if not cap.isOpened():
            logger.error("cv2.VideoCapture could not open temp file: %s", tmp_path)
            return

        fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        # Sample every ~2 seconds; if total frames known, cap at MAX_VIDEO_FRAMES
        frame_interval = max(int(fps * 2), 1)
        if total_frames > 0:
            max_samples = min(MAX_VIDEO_FRAMES, total_frames // frame_interval)
        else:
            max_samples = MAX_VIDEO_FRAMES

        logger.info(
            "Processing video for camera %d: %.1f fps, %d total frames, interval=%d",
            camera_id, fps, total_frames, frame_interval,
        )

        frame_idx = 0
        processed = 0

        while processed < max_samples:
            ret, frame = cap.read()
            if not ret:
                break
            if frame_idx % frame_interval == 0:
                try:
                    ok, buffer = cv2.imencode(".jpg", frame)
                    if ok:
                        detection = detect_people(buffer.tobytes())
                        occupancy = calculate_occupancy(detection["people_count"], capacity)
                        await _push_to_backend(camera_id, detection, occupancy)
                        processed += 1
                except Exception as exc:
                    logger.warning("Frame %d processing error: %s", frame_idx, exc)
                # Yield control so other async tasks aren't starved
                await asyncio.sleep(0.05)
            frame_idx += 1

        cap.release()
        logger.info("Video complete: %d frames processed for camera %d", processed, camera_id)

    except Exception as exc:
        logger.exception("Video background processing failed for camera %d: %s", camera_id, exc)
    finally:
        # Safe cleanup — on Windows, ensure cap is released first
        if tmp_fd is not None:
            try:
                os.close(tmp_fd)
            except OSError:
                pass
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.unlink(tmp_path)
            except OSError as exc:
                logger.warning("Could not delete temp file %s: %s", tmp_path, exc)


# ---------------------------------------------------------------------------
# Stream analysis (RTSP / HTTP / MJPEG / USB webcam)
# ---------------------------------------------------------------------------

@router.post("/stream")
async def analyze_stream(
    camera_id: int,
    stream_url: str,
    capacity: int = 100,
    frames: int = STREAM_FRAMES_DEFAULT,
):
    """
    Open a stream URL and analyse a sample of frames.

    Supported stream types:
      • RTSP CCTV  : rtsp://user:pass@192.168.1.100:554/stream1
      • HTTP MJPEG : http://192.168.1.50:8080/video  (Android IP Webcam)
      • USB webcam : 0, 1, 2  (integer device index as string)
      • HTTP JPEG  : http://host/shot.jpg

    Windows notes:
      • RTSP streams require the FFmpeg backend; install opencv-python (not headless)
        or ensure FFmpeg DLLs are on PATH.
      • CAP_PROP_OPEN_TIMEOUT_MSEC prevents the call from blocking indefinitely.
    """
    if not stream_url:
        raise HTTPException(status_code=400, detail="stream_url is required")

    _validate_stream_url_format(stream_url)

    frames = max(1, min(frames, 30))  # clamp to sane range

    # Resolve integer device index (USB webcam: "0", "1", ...)
    stream_source = _resolve_stream_source(stream_url)

    logger.info("Opening stream: %s (camera_id=%d, frames=%d)", stream_url, camera_id, frames)

    cap = cv2.VideoCapture()
    # Set open timeout BEFORE calling open() — avoids infinite hang on bad RTSP URLs
    cap.set(cv2.CAP_PROP_OPEN_TIMEOUT_MSEC, STREAM_OPEN_TIMEOUT_MS)
    cap.set(cv2.CAP_PROP_READ_TIMEOUT_MSEC, 5000)

    opened = cap.open(stream_source)
    if not opened or not cap.isOpened():
        cap.release()
        raise HTTPException(
            status_code=400,
            detail=f"Cannot open stream '{stream_url}'. "
                   "Check the URL, ensure the camera is online, and that your network allows the connection.",
        )

    frame_count = 0
    last_detection: dict = {}
    last_annotated = None
    last_heatmap = None

    try:
        while frame_count < frames:
            # Retry logic for transient frame read failures (network hiccup)
            frame = None
            for attempt in range(STREAM_READ_RETRIES):
                ret, f = cap.read()
                if ret and f is not None:
                    frame = f
                    break
                logger.debug("Frame read attempt %d failed for %s", attempt + 1, stream_url)
                time.sleep(0.1)

            if frame is None:
                logger.warning("Could not read frame after %d retries", STREAM_READ_RETRIES)
                break

            ok, buffer = cv2.imencode(".jpg", frame)
            if not ok:
                continue
            detection = detect_people(buffer.tobytes())
            last_detection = detection
            if detection["frame_b64"]:
                last_annotated = detection["frame_b64"]
            if detection["heatmap_b64"]:
                last_heatmap = detection["heatmap_b64"]
            frame_count += 1

            # Skip ahead for efficiency (avoids analysing near-duplicate frames)
            _skip_frames(cap, 10)

    finally:
        cap.release()

    if frame_count == 0:
        raise HTTPException(
            status_code=400,
            detail="Stream opened but no frames could be captured. The stream may be stalled.",
        )

    # Report the count from the LAST frame analysed — i.e. who is visible
    # right now — not a sum/average across the sampled frames.
    current_people = last_detection.get("people_count", 0)
    occupancy = calculate_occupancy(current_people, capacity)
    combined_detection = {
        "people_count": current_people,
        "frame_b64": last_annotated,
        "heatmap_b64": last_heatmap,
    }
    await _push_to_backend(camera_id, combined_detection, occupancy)

    return {
        "success": True,
        "camera_id": camera_id,
        "frames_analyzed": frame_count,
        **occupancy,
        "frame_b64": last_annotated,
        "heatmap_b64": last_heatmap,
    }


@router.post("/stream/start")
async def start_stream_session(
    camera_id: int = Query(...),
    stream_url: str = Query(...),
    capacity: int = Query(100),
    frames: int = Query(STREAM_FRAMES_DEFAULT),
):
    """
    Start (or no-op into) a continuous analysis SESSION for a camera.

    Unlike POST /api/analyze/stream (which opens the camera, reads a batch
    of frames, and closes it again — meant to be called once), this opens
    the VideoCapture exactly ONCE and keeps it open, reading frames
    continuously in the background, until /stream/stop is called for the
    same camera_id.

    Idempotent: if a session is already running for this camera_id, the
    camera is NOT reopened — the existing session just keeps running and
    this returns "already_running".
    """
    if not stream_url:
        raise HTTPException(status_code=400, detail="stream_url is required")

    _validate_stream_url_format(stream_url)

    frames = max(1, min(frames, 30))

    with _sessions_lock:
        existing = _sessions.get(camera_id)
        if existing is not None and existing.is_alive():
            logger.info("Analyze Now ignored for camera %d — session already running", camera_id)
            return {
                "success": True,
                "camera_id": camera_id,
                "status": "already_running",
                "message": "Continuous analysis is already running for this camera; camera was not reopened.",
            }

        session = StreamSession(camera_id, stream_url, capacity, frames)
        thread = threading.Thread(
            target=_run_session, args=(session,), daemon=True, name=f"stream-session-{camera_id}"
        )
        session.thread = thread
        _sessions[camera_id] = session
        thread.start()

    logger.info(
        "Started continuous analysis session for camera %d (stream=%s, batch=%d frames)",
        camera_id, stream_url, frames,
    )
    return {
        "success": True,
        "camera_id": camera_id,
        "status": "started",
        "message": "Continuous analysis session started — camera opened once and stays open until stopped.",
    }


@router.post("/stream/stop")
async def stop_stream_session(camera_id: int = Query(...)):
    """
    Stop the continuous analysis session for a camera and release its
    VideoCapture. Safe to call even if no session is running.
    """
    with _sessions_lock:
        session = _sessions.pop(camera_id, None)

    if session is None:
        return {
            "success": True,
            "camera_id": camera_id,
            "status": "not_running",
            "message": "No active analysis session for this camera.",
        }

    session.stop_event.set()
    if session.thread is not None:
        session.thread.join(timeout=5.0)  # give the loop a moment to release the capture gracefully

    logger.info("Stopped continuous analysis session for camera %d", camera_id)
    return {
        "success": True,
        "camera_id": camera_id,
        "status": "stopped",
        "message": "Analysis session stopped and camera released.",
    }


@router.get("/stream/status")
async def stream_session_status(camera_id: int = Query(...)):
    """Report whether a continuous analysis session is currently active for a camera."""
    with _sessions_lock:
        session = _sessions.get(camera_id)
        running = session is not None and session.is_alive()
    return {"camera_id": camera_id, "is_analyzing": running}


def stop_all_sessions() -> None:
    """Stop every active session — called on application shutdown."""
    with _sessions_lock:
        sessions = list(_sessions.values())
        _sessions.clear()
    for session in sessions:
        session.stop_event.set()
    for session in sessions:
        if session.thread is not None:
            session.thread.join(timeout=5.0)


def _open_capture(stream_url: str):
    """Attempt to open a VideoCapture once. Returns the capture or None."""
    stream_source = _resolve_stream_source(stream_url)
    cap = cv2.VideoCapture()
    cap.set(cv2.CAP_PROP_OPEN_TIMEOUT_MSEC, STREAM_OPEN_TIMEOUT_MS)
    cap.set(cv2.CAP_PROP_READ_TIMEOUT_MSEC, 5000)
    opened = cap.open(stream_source)
    if not opened or not cap.isOpened():
        cap.release()
        return None
    return cap


def _run_session(session: "StreamSession") -> None:
    """
    Background-thread loop for one camera's continuous analysis session.

    Opens the VideoCapture once at session start, then repeatedly reads a
    batch of `frames_per_batch` frames (kept for a fresh annotated
    snapshot/heatmap and to skip ahead efficiently on the stream), and
    pushes a result to the backend after each batch, until
    session.stop_event is set.

    COUNTING POLICY: the pushed people_count is always the count from the
    LAST frame read in the batch — i.e. who is visible RIGHT NOW — never a
    sum or average across frames. There is no cumulative/historical
    counting: if someone leaves the frame the count drops immediately, and
    if they (or anyone else) re-enter later, they're counted again.

    STREAM RECOVERY: if a batch fails to read any frames (stream ended
    prematurely / frame read failed), the capture is treated as dead —
    instead of retrying forever on the same broken handle, we close it,
    wait 2 seconds, and reopen the stream automatically, then continue the
    SAME analysis session (no new "Analyze Now" click required). This
    keeps retrying (close/wait/reopen) until Stop Analysis is pressed.
    """
    cap = _open_capture(session.stream_url)
    if cap is None:
        logger.error(
            "Session camera %d: could not open stream '%s' — session aborted",
            session.camera_id, session.stream_url,
        )
        with _sessions_lock:
            # Only remove ourselves if we're still the registered session
            # (avoids clobbering a session that was already replaced/stopped).
            if _sessions.get(session.camera_id) is session:
                _sessions.pop(session.camera_id, None)
        return

    session.cap = cap
    logger.info("Session camera %d: stream opened once, continuous analysis running", session.camera_id)

    try:
        while not session.stop_event.is_set():
            current_count = None   # count from the LAST frame read this batch
            frame_count = 0
            last_annotated = None
            last_heatmap = None

            for _ in range(session.frames_per_batch):
                if session.stop_event.is_set():
                    break

                frame = None
                for attempt in range(STREAM_READ_RETRIES):
                    ret, f = cap.read()
                    if ret and f is not None:
                        frame = f
                        break
                    time.sleep(0.1)

                if frame is None:
                    logger.warning(
                        "Session camera %d: frame read failed after %d retries",
                        session.camera_id, STREAM_READ_RETRIES,
                    )
                    break

                ok, buffer = cv2.imencode(".jpg", frame)
                if not ok:
                    continue

                detection = detect_people(buffer.tobytes())
                # Overwrite (not accumulate) — only the MOST RECENT frame's
                # count matters for "who is visible right now".
                current_count = detection["people_count"]
                if detection["frame_b64"]:
                    last_annotated = detection["frame_b64"]
                if detection["heatmap_b64"]:
                    last_heatmap = detection["heatmap_b64"]
                frame_count += 1

                _skip_frames(cap, 10)

            if frame_count > 0:
                # current_count reflects ONLY the most recent frame that was
                # just analysed — never a sum/average across frames, and
                # never carried over from a previous batch.
                occupancy = calculate_occupancy(current_count, session.capacity)
                combined_detection = {
                    "people_count": current_count,
                    "frame_b64": last_annotated,
                    "heatmap_b64": last_heatmap,
                }
                _push_to_backend_sync(session.camera_id, combined_detection, occupancy)
                session.stop_event.wait(timeout=STREAM_BATCH_INTERVAL_SEC)
                continue

            # No frames could be read this batch — the stream ended
            # prematurely or is stalled. Do NOT spin retrying on the same
            # broken handle: close it, wait 2 seconds, and reopen
            # automatically, then keep going on the SAME session. Repeats
            # until Stop Analysis is pressed.
            logger.warning(
                "Session camera %d: stream read failing — closing, waiting 2s, and reopening",
                session.camera_id,
            )
            cap.release()
            session.cap = None

            if session.stop_event.wait(timeout=STREAM_RECOVERY_WAIT_SEC):
                break  # Stop Analysis was pressed during the recovery wait

            reopened = None
            while not session.stop_event.is_set():
                reopened = _open_capture(session.stream_url)
                if reopened is not None:
                    break
                logger.warning(
                    "Session camera %d: reopen attempt failed, retrying in %.0fs",
                    session.camera_id, STREAM_RECOVERY_WAIT_SEC,
                )
                if session.stop_event.wait(timeout=STREAM_RECOVERY_WAIT_SEC):
                    break

            if reopened is None:
                break  # stop was requested while trying to recover

            cap = reopened
            session.cap = cap
            logger.info("Session camera %d: stream reopened, resuming same analysis session", session.camera_id)
    finally:
        if cap is not None:
            cap.release()
        session.cap = None
        logger.info("Session camera %d: capture released, analysis stopped", session.camera_id)


def _push_to_backend_sync(camera_id: int, detection: dict, occupancy: dict) -> None:
    """
    Synchronous counterpart of _push_to_backend, used from the background
    session thread (which is not running inside the asyncio event loop).
    Mirrors the same status-code checking so failures are never silent.
    """
    try:
        url = f"{BACKEND_URL}/api/monitoring/cameras/{camera_id}/data"
        params = {"peopleCount": occupancy["people_count"]}

        files: dict = {}
        if detection.get("frame_b64"):
            files["frame"] = ("frame.jpg", base64.b64decode(detection["frame_b64"]), "image/jpeg")
        if detection.get("heatmap_b64"):
            files["heatmap"] = ("heatmap.jpg", base64.b64decode(detection["heatmap_b64"]), "image/jpeg")

        response = requests.post(url, params=params, files=files or None, timeout=15.0)

        if response.status_code >= 400:
            logger.error(
                "Session: backend REJECTED crowd data for camera %d (HTTP %d): %s",
                camera_id, response.status_code, response.text,
            )
        else:
            logger.info(
                "Session pushed crowd data for camera %d: %d people, %.1f%% occupancy -> accepted (HTTP %d)",
                camera_id, occupancy["people_count"], occupancy["occupancy_percentage"],
                response.status_code,
            )
    except requests.exceptions.ConnectionError:
        logger.error(
            "Session: backend not reachable at %s — results NOT saved (is Spring Boot running?)",
            BACKEND_URL,
        )
    except Exception as exc:
        logger.error("Session: unexpected error pushing crowd data to backend: %s", exc)


@router.get("/validate-stream")
async def validate_stream(stream_url: str = Query(...)):
    """
    Quick check whether a stream URL is reachable.
    Tries to open it with a short timeout and read one frame.
    """
    if not stream_url:
        return {"valid": False, "message": "No URL provided"}

    try:
        _validate_stream_url_format(stream_url)
    except HTTPException as exc:
        return {"valid": False, "message": exc.detail}

    stream_source = _resolve_stream_source(stream_url)
    cap = cv2.VideoCapture()
    cap.set(cv2.CAP_PROP_OPEN_TIMEOUT_MSEC, 5000)
    cap.set(cv2.CAP_PROP_READ_TIMEOUT_MSEC, 3000)

    try:
        opened = cap.open(stream_source)
        if not opened:
            return {"valid": False, "message": "Could not connect to stream"}
        ret, _ = cap.read()
        if not ret:
            return {"valid": False, "message": "Connected but no frames available"}
        return {"valid": True, "message": "Stream is reachable"}
    except Exception as exc:
        return {"valid": False, "message": str(exc)}
    finally:
        cap.release()


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _resolve_stream_source(stream_url: str):
    """
    Convert a stream URL string to the correct type for cv2.VideoCapture.

    USB webcam indices ("0", "1", "2") are converted to int so OpenCV uses
    the correct DirectShow / V4L2 backend instead of trying to open a file.
    """
    stripped = stream_url.strip()
    if stripped.isdigit():
        return int(stripped)
    return stripped


# Schemes accepted before we even attempt to open a capture. Covers CCTV
# (rtsp), mobile IP-camera apps (IP Webcam / DroidCam / Iriun — http/https,
# often with a /video or /stream path and a non-standard port), and plain
# USB webcam device indices ("0", "1", ...).
_SUPPORTED_STREAM_SCHEMES = ("rtsp://", "http://", "https://")


def _validate_stream_url_format(stream_url: str) -> None:
    """
    Cheap, immediate validation of a stream URL's FORMAT — before we ever
    try to open a VideoCapture (which can take seconds to time out on a
    bad host). Raises HTTPException with a clear, actionable message for
    anything that isn't a supported scheme or a bare USB device index.

    Supported:
      • rtsp://user:pass@ip:554/stream   (CCTV IP cameras)
      • http://ip:port[/video|/stream]   (IP Webcam, DroidCam, Iriun, MJPEG)
      • https://ip:port[/video|/stream]
      • "0", "1", "2", ...               (USB webcam device index)
    """
    stripped = (stream_url or "").strip()
    if not stripped:
        raise HTTPException(status_code=400, detail="Stream URL cannot be empty.")

    if stripped.isdigit():
        return  # USB webcam device index — always valid format

    if not stripped.lower().startswith(_SUPPORTED_STREAM_SCHEMES):
        raise HTTPException(
            status_code=400,
            detail=(
                f"Unsupported stream URL '{stream_url}'. Use rtsp:// for CCTV "
                "cameras, or http:// / https:// for mobile IP-camera apps "
                "(IP Webcam, DroidCam, Iriun) — e.g. http://192.168.x.x:8080/video."
            ),
        )

    # Must have SOMETHING after the scheme (a host)
    remainder = stripped.split("://", 1)[1]
    if not remainder:
        raise HTTPException(
            status_code=400,
            detail=f"Stream URL '{stream_url}' is missing a host/IP address.",
        )


def _skip_frames(cap: cv2.VideoCapture, n: int) -> None:
    """Advance the capture position by n frames (best-effort)."""
    current = cap.get(cv2.CAP_PROP_POS_FRAMES)
    if current >= 0:
        cap.set(cv2.CAP_PROP_POS_FRAMES, current + n)
    else:
        # For live streams CAP_PROP_POS_FRAMES returns -1; just grab without decode
        for _ in range(n):
            cap.grab()


async def _push_to_backend(camera_id: int, detection: dict, occupancy: dict) -> None:
    """
    POST crowd analysis results to the Spring Boot backend.

    Endpoint:  POST /api/monitoring/cameras/{camera_id}/data
    Params:    peopleCount (query param, matches @RequestParam in MonitoringController)
    Files:     frame   (optional multipart JPEG)
               heatmap (optional multipart JPEG)

    CRITICAL FIX: httpx does NOT raise an exception for non-2xx HTTP status
    codes by default — a 400/500 response from the backend was previously
    completely silent. The AI service logged nothing and reported "success"
    for the video upload, while the detected people count simply vanished.
    This is the most likely cause of "video counts people successfully but
    dashboard never shows it." We now explicitly check response.status_code
    and log the full response body on any non-2xx result.
    """
    try:
        url = f"{BACKEND_URL}/api/monitoring/cameras/{camera_id}/data"
        # peopleCount matches the Spring Boot @RequestParam name
        params = {"peopleCount": occupancy["people_count"]}

        files: dict = {}
        if detection.get("frame_b64"):
            files["frame"] = (
                "frame.jpg",
                base64.b64decode(detection["frame_b64"]),
                "image/jpeg",
            )
        if detection.get("heatmap_b64"):
            files["heatmap"] = (
                "heatmap.jpg",
                base64.b64decode(detection["heatmap_b64"]),
                "image/jpeg",
            )

        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(url, params=params, files=files or None)

            if response.status_code >= 400:
                # THIS check was missing — without it, a backend 400/500
                # silently discarded every detection result with zero trace.
                logger.error(
                    "Backend REJECTED crowd data for camera %d (HTTP %d): %s",
                    camera_id, response.status_code, response.text,
                )
            else:
                logger.info(
                    "Pushed crowd data for camera %d: %d people, %.1f%% occupancy -> accepted (HTTP %d)",
                    camera_id, occupancy["people_count"], occupancy["occupancy_percentage"],
                    response.status_code,
                )

    except httpx.ConnectError:
        logger.error(
            "Backend not reachable at %s — results NOT saved (is Spring Boot running?)",
            BACKEND_URL,
        )
    except Exception as exc:
        logger.error("Unexpected error pushing crowd data to backend: %s", exc)
