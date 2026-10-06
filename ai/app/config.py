"""Configuration for the CYPHER AI engine.

Everything is overridable by environment variable so the service can point at a
different gateway or camera without a code change.
"""
import os


class Settings:
    # ---- gateway -----------------------------------------------------------
    # The Spring Boot service that owns the database. Detections are POSTed here
    # (MoSCoW M-05: the AI-to-gateway ingestion contract).
    GATEWAY_URL: str = os.getenv("GATEWAY_URL", "http://localhost:8081")

    # A service account, because /api/** requires a bearer token.
    SERVICE_EMAIL: str = os.getenv("CYPHER_SERVICE_EMAIL", "admin@cypher.com")
    SERVICE_PASSWORD: str = os.getenv("CYPHER_SERVICE_PASSWORD", "Admin@2026")

    # ---- detection ---------------------------------------------------------
    # Path to the fine-tuned weapon detector produced by ai/train.py.
    WEIGHTS: str = os.getenv("YOLO_WEIGHTS", "ai/models/weapon_best.pt")

    # Minimum confidence before a detection is reported as an incident.
    CONFIDENCE: float = float(os.getenv("YOLO_CONFIDENCE", "0.45"))

    # Inference resolution. 416 keeps latency low enough for a live demo.
    IMG_SIZE: int = int(os.getenv("YOLO_IMGSZ", "416"))

    # Which camera row in the database this engine reports against. Defaults to
    # the first camera returned by the gateway when unset.
    CAMERA_ID: int | None = (
        int(os.environ["CYPHER_CAMERA_ID"]) if os.getenv("CYPHER_CAMERA_ID") else None
    )

    # ---- behaviour ---------------------------------------------------------
    # Seconds before the same object class on the same camera may raise another
    # incident. Without this a 30 fps stream would create 30 incidents a second.
    #
    # 90s rather than 30s: in a 45-second run this created three incidents for
    # two object classes, which fills the operator's stream with duplicates
    # during a demo.
    COOLDOWN_SECONDS: int = int(os.getenv("INCIDENT_COOLDOWN", "90"))

    # Object classes the gateway treats as CRITICAL. Kept here only for logging;
    # the authoritative rule lives in the gateway's IncidentService.
    CRITICAL_CLASSES = {"weapon", "knife", "pistol", "gun", "fire"}


settings = Settings()
