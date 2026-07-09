"""
ModelLoader — singleton wrapper around YOLOv8n.

Windows compatibility notes:
- YOLO downloads yolov8n.pt to the current working directory on first run.
  If the working directory is read-only, set YOLO_WEIGHTS_DIR to a writable path.
- YOLO_VERBOSE=False suppresses per-frame console spam on Windows terminals.
- Torch compile (used internally by newer Ultralytics) can fail on Windows with
  certain CUDA versions; we disable it via PYTORCH_NO_CUDA_MEMORY_CACHING.
"""

import logging
import os

logger = logging.getLogger(__name__)


class ModelLoader:
    _model = None

    @classmethod
    def load_model(cls):
        if cls._model is not None:
            return cls._model

        # Suppress verbose YOLO / torch output — important on Windows terminals
        os.environ.setdefault("YOLO_VERBOSE", "False")
        # Prevent torch from trying to compile kernels (can fail on Windows without VS build tools)
        os.environ.setdefault("PYTORCH_JIT", "0")

        logger.info("Downloading / loading YOLOv8n model...")
        try:
            from ultralytics import YOLO

            # Allow user to redirect model storage via env var (useful on Windows
            # when the working directory is inside Program Files or another restricted path)
            weights_path = os.getenv("YOLO_WEIGHTS_DIR", "yolov8n.pt")
            if os.path.isdir(weights_path):
                weights_path = os.path.join(weights_path, "yolov8n.pt")

            cls._model = YOLO(weights_path)
            logger.info("YOLOv8n model loaded successfully")
        except Exception as exc:
            logger.error("Failed to load YOLO model: %s", exc)
            raise RuntimeError(f"YOLO model load failed: {exc}") from exc

        return cls._model

    @classmethod
    def get_model(cls):
        if cls._model is None:
            cls.load_model()
        return cls._model
