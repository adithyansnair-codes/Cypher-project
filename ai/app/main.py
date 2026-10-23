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
from fastapi.middleware.cors import CORSMiddleware
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

# The cockpit is served by the Spring gateway on 8081, while inference is a
# separate FastAPI process on 5000. The browser therefore makes a cross-origin
# request. Keep the allow-list to the local cockpit and the deployed cockpit.
AI_CORS_ORIGINS = [
    origin.strip()
    for origin in os.getenv(
        "AI_CORS_ORIGINS",
        "http://localhost:8081,http://127.0.0.1:8081,"
        "http://13.207.55.75:8081,http://13.207.55.75:8080",
    ).split(",")
    if origin.strip()
]
app.add_middleware(
    CORSMiddleware,
    allow_origins=AI_CORS_ORIGINS,
    allow_origin_regex=r"^http://(localhost|127\.0\.0\.1)(:\d+)?$",
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
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

# The live trace in the cockpit is throttled to this many seconds per camera:
# reasoning is worth watching, but not 27 times a second. Decisions
# (incidents, blocks) bypass the throttle because they are the interesting part.
TRACE_MIN_INTERVAL = float(os.getenv("TRACE_MIN_INTERVAL", "1.5"))
_last_trace: dict[int, float] = defaultdict(float)


@app.post("/demo/reset")
def reset_demo(camera_id: int | None = None) -> dict[str, Any]:
    """Clear only the in-memory streak/cooldown state before a repeatable demo.

    This is deliberately an explicit demo control. It does not touch the
    database; it only forgets transient frame counters in this AI process.
    """
    for state in (_streak, _last_raised):
        keys = [
            key for key in list(state)
            if camera_id is None or key[0] == camera_id
        ]
        for key in keys:
            state.pop(key, None)
    return {"status": "reset", "cameraId": camera_id}


def _trace(
    camera_id: int,
    steps: list[str],
    level: str = "INFO",
    force: bool = False,
    inference_ms: float | None = None,
) -> None:
    """Stream reasoning to the cockpit, throttled per camera."""
    now = time.time()
    if not force and (now - _last_trace[camera_id]) < TRACE_MIN_INTERVAL:
        return
    _last_trace[camera_id] = now
    gateway.post_trace(
        camera_id,
        steps,
        level=level,
        model=Path(detector.weights).name if detector else None,
        threshold=settings.CONFIDENCE,
        inference_ms=inference_ms,
    )


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
    started = time.perf_counter()
    found = detector.detect(frame)
    inference_ms = (time.perf_counter() - started) * 1000

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

    header = [
        f"frame received: {frame.shape[1]}x{frame.shape[0]} px",
        f"YOLO inference: {len(found)} box(es) in {inference_ms:.0f} ms "
        f"at threshold {settings.CONFIDENCE:.2f}",
    ]
    for label, det in best.items():
        header.append(
            f"detection: {label} {det['confidence']:.1f}% "
            f"box {det['box']}"
        )
    if duplicates > 0:
        header.append(
            f"dedupe: {duplicates} overlapping box(es) collapsed -> "
            f"{len(best)} object class(es)"
        )

    for label, det in best.items():
        cid = camera_id or gateway.default_camera_id()
        _streak[(cid, label)] += 1

        if _streak[(cid, label)] < CONFIRM_FRAMES:
            skipped.append(f"{label} (needs {CONFIRM_FRAMES} frames, has {_streak[(cid, label)]})")
            _trace(cid, header + [
                f"streak: {label} {_streak[(cid, label)]}/{CONFIRM_FRAMES} "
                f"-> holding, one frame is not evidence"
            ], level="BLOCKED")
            continue
        if _on_cooldown(cid, label):
            skipped.append(f"{label} (cooldown)")
            _trace(cid, header + [
                f"cooldown: {label} already raised within {settings.COOLDOWN_SECONDS}s "
                f"-> suppressed"
            ], level="BLOCKED", force=True)
            continue

        # Only weapons and fire are worth waking an operator for from vision
        # alone; the gateway decides the severity.
        if not _is_reportable(label):
            skipped.append(f"{label} (not reportable)")
            _trace(cid, header + [
                f"policy: {label} is not on the reportable list -> not an incident"
            ], level="BLOCKED", force=True)
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
                trace=header + [
                    f"streak: {label} confirmed on {CONFIRM_FRAMES} consecutive frames",
                    f"decision: report {label} to the gateway for priority grading",
                ],
                model=Path(detector.weights).name,
                threshold=settings.CONFIDENCE,
                inference_ms=round(inference_ms, 1),
                bounding_box=det["box"],
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

            frame_started = time.perf_counter()
            detections = detector.detect(frame)
            inference_ms = (time.perf_counter() - frame_started) * 1000
            labels_this_frame = {str(d["label"]).lower() for d in detections}
            best_this_frame: dict[str, dict[str, Any]] = {}
            for d in detections:
                label = str(d["label"]).lower()
                seen[label] += 1
                if (label not in best_this_frame
                        or float(d["confidence"]) > float(best_this_frame[label]["confidence"])):
                    best_this_frame[label] = d

            # advance the streak for labels present, reset it for those absent
            for label in list(_streak):
                if label[0] == cid and label[1] not in labels_this_frame:
                    _streak[label] = 0
            for label in labels_this_frame:
                _streak[(cid, label)] += 1

            observed = ", ".join(
                f"{name} {det['confidence']:.0f}%"
                for name, det in sorted(best_this_frame.items())
            ) or "nothing above threshold"
            _trace(
                cid,
                [
                    f"frame {frames}: {observed}",
                    f"inference {inference_ms:.0f} ms, {len(detections)} box(es)",
                    *[
                        f"streak {name}: {_streak[(cid, name)]}/{CONFIRM_FRAMES}"
                        for name in sorted(labels_this_frame)
                    ],
                ],
                inference_ms=round(inference_ms, 1),
            )

            for label, det in best_this_frame.items():
                confidence = float(det["confidence"])
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
                        trace=[
                            f"frame {frames}: {label} {confidence:.0f}%",
                            f"streak: confirmed on {CONFIRM_FRAMES} consecutive frames "
                            f"-> reporting to the gateway for priority grading",
                        ],
                        model=Path(detector.weights).name,
                        threshold=settings.CONFIDENCE,
                        inference_ms=round(inference_ms, 1),
                        bounding_box=det["box"],
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
    # "lying" is the class the six-class model produces for a fallen person;
    # the platform's health rule treats it like "fall".
    return label in settings.CRITICAL_CLASSES or label in {"smoke", "fall", "person", "lying"}
