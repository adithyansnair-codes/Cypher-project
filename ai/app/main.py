"""CYPHER AI engine -- FastAPI service.

Runs YOLO detection and reports findings to the Spring Boot gateway, which owns
the database. This service holds no persistent state.

Run:
    uvicorn ai.app.main:app --host 0.0.0.0 --port 5000

Endpoints:
    GET  /health          liveness plus model info
    POST /detect          run detection on an uploaded image, no side effects
    POST /monitor/frame   detect AND raise incidents for what it finds
    POST /monitor/stream  background worker over a video file or camera index
"""
from __future__ import annotations

import logging
import os
import time
from collections import defaultdict
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from .config import settings
from .detector import Detector
from .gateway import GatewayClient

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s %(name)s  %(message)s",
)
log = logging.getLogger("cypher.ai")

app = FastAPI(
    title="CYPHER AI Engine",
    description="Computer-vision incident detection for the CYPHER platform",
    version="1.0.0",
)

detector: Detector | None = None
gateway = GatewayClient()

# camera_id -> label -> last time an incident was raised for it
_last_raised: dict[tuple[int, str], float] = defaultdict(float)

# (camera_id, label) -> how many frames in a row have shown it.
#
# WHY: a single frame is not evidence. In a 45-second webcam run the model
# scored "pistol" on 1282 frames at 35-55% confidence when no pistol was
# present, and one of those frames was enough to raise a CRITICAL incident.
# A real object is seen consistently; sensor noise and a familiar shape are
# not. Requiring the same label across several frames removes most of it.
_streak: dict[tuple[int, str], int] = defaultdict(int)

# Frames in a row a label must appear before it is reported. At ~27 fps this is
# roughly a third of a second -- short enough that an operator does not notice,
# long enough to reject a one-frame false positive.
CONFIRM_FRAMES = int(os.getenv("CONFIRM_FRAMES", "8"))


@app.on_event("startup")
def _startup() -> None:
    global detector
    detector = Detector()
    log.info("gateway reachable: %s", gateway.healthy())


# --------------------------------------------------------------------- health
@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP",
        "service": "cypher-ai",
        "weights": detector.weights if detector else None,
        "classes": list(detector.classes.values()) if detector else [],
        "weapon_model": detector.is_weapon_model() if detector else False,
        "confidence_threshold": settings.CONFIDENCE,
        "gateway": settings.GATEWAY_URL,
        "gateway_reachable": gateway.healthy(),
    }


# --------------------------------------------------------------------- detect
@app.post("/detect")
async def detect(file: UploadFile = File(...)) -> JSONResponse:
    """Detect objects in an uploaded image. No incidents are created."""
    frame = await _read_upload(file)
    found = detector.detect(frame)
    return JSONResponse(
        {
            "detections": found,
            "count": len(found),
            "weights": Path(detector.weights).name,
        }
    )


@app.post("/monitor/frame")
async def monitor_frame(
    file: UploadFile = File(...), camera_id: int | None = None
) -> JSONResponse:
    """Detect, then raise an incident for each reportable finding.

    This is the endpoint that closes MoSCoW M-05: a model sees something and an
    incident appears in the cockpit.
    """
    frame = await _read_upload(file)
    found = detector.detect(frame)

    raised: list[dict[str, Any]] = []
    skipped: list[str] = []

    # One incident per object class per frame. A single image often contains
    # several boxes for the same object (overlapping detections); reporting each
    # would raise five identical incidents for one photograph.
    best: dict[str, dict[str, Any]] = {}
    for det in found:
        label = str(det["label"]).lower()
        if label not in best or det["confidence"] > best[label]["confidence"]:
            best[label] = det

    duplicates = len(found) - len(best)
    if duplicates > 0:
        log.info("collapsed %d duplicate detection(s) into %d object class(es)",
                 duplicates, len(best))

    for label, det in best.items():
        cid = camera_id or gateway.default_camera_id()
        _streak[(cid, label)] += 1

        if _streak[(cid, label)] < CONFIRM_FRAMES:
            skipped.append(f"{label} (needs {CONFIRM_FRAMES} frames, has {_streak[(cid, label)]})")
            continue
        if _on_cooldown(cid, label):
            skipped.append(f"{label} (cooldown)")
            continue

        # Only weapons and fire are worth waking an operator for from vision
        # alone; the gateway decides the severity.
        if not _is_reportable(label):
            skipped.append(f"{label} (not reportable)")
            continue

        try:
            incident = gateway.report_incident(
                title=f"{label.capitalize()} detected",
                detected_object=label,
                confidence=det["confidence"],
                description=(
                    f"{label} detected by the AI engine at "
                    f"{det['confidence']:.1f}% confidence."
                ),
                camera_id=cid,
            )
            _last_raised[(cid, label)] = time.time()
            raised.append(incident)
        except Exception as exc:  # noqa: BLE001 - report, do not crash the loop
            log.error("failed to report %s: %s", label, exc)
            skipped.append(f"{label} (report failed: {exc})")

    return JSONResponse(
        {
            "detections": found,
            "incidents_raised": raised,
            "skipped": skipped,
            "weights": Path(detector.weights).name,
        }
    )


# --------------------------------------------------------------------- stream
@app.post("/monitor/stream")
def monitor_stream(source: str, max_seconds: int = 30, camera_id: int | None = None) -> dict[str, Any]:
    """Run detection over a video file or camera index for a bounded time.

    `source` is a path or an integer camera index as a string (e.g. "0" for the
    default webcam).
    """
    src: Any = int(source) if str(source).isdigit() else source
    if isinstance(src, str) and not Path(src).exists():
        raise HTTPException(status_code=404, detail=f"no such video: {src}")

    cap = cv2.VideoCapture(src)
    if not cap.isOpened():
        raise HTTPException(status_code=400, detail=f"cannot open video source {source}")

    started = time.time()
    frames = 0
    raised = 0
    seen: dict[str, int] = defaultdict(int)
    reported: set[str] = set()
    cid = camera_id or gateway.default_camera_id()

    try:
        while (time.time() - started) < max_seconds:
            ok, frame = cap.read()
            if not ok:
                break
            frames += 1

            detections = detector.detect(frame)
            labels_this_frame = {str(d["label"]).lower() for d in detections}
            best_this_frame: dict[str, float] = {}
            for d in detections:
                label = str(d["label"]).lower()
                seen[label] += 1
                best_this_frame[label] = max(
                    best_this_frame.get(label, 0.0), float(d["confidence"])
                )

            # advance the streak for labels present, reset it for those absent
            for label in list(_streak):
                if label[0] == cid and label[1] not in labels_this_frame:
                    _streak[label] = 0
            for label in labels_this_frame:
                _streak[(cid, label)] += 1

            for label, confidence in best_this_frame.items():
                if not _is_reportable(label) or _on_cooldown(cid, label):
                    continue
                if _streak[(cid, label)] < CONFIRM_FRAMES:
                    continue  # seen too briefly to trust
                if label in reported:
                    continue
                try:
                    gateway.report_incident(
                        title=f"{label.capitalize()} detected",
                        detected_object=label,
                        confidence=confidence,
                        description=f"{label} detected in a video stream.",
                        camera_id=cid,
                    )
                    _last_raised[(cid, label)] = time.time()
                    reported.add(label)
                    raised += 1
                except Exception as exc:  # noqa: BLE001
                    log.error("report failed: %s", exc)
    finally:
        cap.release()

    return {
        "frames_processed": frames,
        "seconds": round(time.time() - started, 1),
        "objects_seen": dict(seen),
        "confirmed": sorted(reported),
        "confirmation_frames": CONFIRM_FRAMES,
        "incidents_raised": raised,
    }


# -------------------------------------------------------------------- helpers
async def _read_upload(file: UploadFile) -> np.ndarray:
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="empty upload")
    buffer = np.frombuffer(raw, np.uint8)
    frame = cv2.imdecode(buffer, cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(status_code=400, detail="not a readable image")
    return frame


def _on_cooldown(camera_id: int, label: str) -> bool:
    last = _last_raised.get((camera_id, label), 0.0)
    return (time.time() - last) < settings.COOLDOWN_SECONDS


def _is_reportable(label: str) -> bool:
    return label in settings.CRITICAL_CLASSES or label in {"smoke", "fall", "person"}
