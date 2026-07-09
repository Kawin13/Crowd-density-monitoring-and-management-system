"""health.py — liveness and readiness endpoints."""

from fastapi import APIRouter
from utils.model_loader import ModelLoader

router = APIRouter()


@router.get("/health")
def health_check():
    """Liveness probe — always returns 200 so the process is known to be up."""
    return {
        "status": "healthy",
        "model": "yolov8n",
        "model_loaded": ModelLoader._model is not None,
        "service": "Crowd Density AI Service",
    }


@router.get("/ready")
def readiness_check():
    """Readiness probe — returns 503 until the YOLO model is loaded."""
    if ModelLoader._model is None:
        from fastapi.responses import JSONResponse
        return JSONResponse(
            status_code=503,
            content={"status": "not_ready", "message": "Model not yet loaded"},
        )
    return {"status": "ready", "model": "yolov8n"}
