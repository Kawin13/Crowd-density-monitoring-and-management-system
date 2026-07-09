"""
main.py — FastAPI application entry point for the Crowd Density AI Service.

Windows startup notes:
  1. Activate your venv first:  venv\\Scripts\\activate
  2. Run:                        python main.py
  3. The app listens on:         http://0.0.0.0:8000
  4. Interactive API docs:       http://localhost:8000/docs

The YOLO model (yolov8n.pt, ~6 MB) is downloaded automatically on first startup.
If your machine has no internet access, copy yolov8n.pt into the ai-service folder
and set YOLO_WEIGHTS_DIR=. in your .env file.
"""

import logging
import os
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from routers import analyze, health
from utils.model_loader import ModelLoader

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("=== Crowd Density AI Service starting ===")
    try:
        ModelLoader.load_model()
        logger.info("YOLOv8n model ready — all endpoints available")
    except Exception as exc:
        logger.error(
            "Model load failed: %s\n"
            "The service will start but /api/analyze/* endpoints will return 500.\n"
            "Ensure torch and ultralytics are installed:  pip install -r requirements.txt",
            exc,
        )
    yield
    logger.info("=== AI Service shutting down ===")
    try:
        analyze.stop_all_sessions()
        logger.info("All active continuous analysis sessions stopped and cameras released")
    except Exception as exc:
        logger.warning("Error while stopping analysis sessions on shutdown: %s", exc)


app = FastAPI(
    title="Crowd Density AI Service",
    description=(
        "YOLOv8n-based real-time crowd detection and density analysis. "
        "Supports RTSP CCTV cameras, Android IP Webcam streams, USB webcams, "
        "HTTP/MJPEG streams, and uploaded video files."
    ),
    version="2.0.0",
    lifespan=lifespan,
)

# Allow all origins so the React frontend (localhost:5173) can call directly
# and the Spring Boot backend (localhost:8080) can call from a server context.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router,   prefix="/api",         tags=["Health"])
app.include_router(analyze.router,  prefix="/api/analyze", tags=["Analysis"])


if __name__ == "__main__":
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=port,
        reload=False,   # reload=True causes issues with YOLO model state on Windows
        workers=1,      # keep single worker — model is loaded once into class state
    )
