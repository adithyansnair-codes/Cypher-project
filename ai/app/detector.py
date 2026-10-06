"""YOLO detector wrapper.

Fine-tuned weights if present, otherwise the stock COCO model so the service
still runs (and can still detect people) before training has finished.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from ultralytics import YOLO

from .config import settings

log = logging.getLogger("cypher.detector")

# COCO class ids we care about when running stock weights.
COCO_PERSON = 0


class Detector:
    def __init__(self) -> None:
        weights = settings.WEIGHTS
        if not Path(weights).exists():
            log.warning(
                "fine-tuned weights not found at %s -- falling back to yolov8n.pt "
                "(stock COCO model: detects people, not weapons)",
                weights,
            )
            weights = "yolov8n.pt"

        self.model = YOLO(weights)
        self.weights = weights
        self.classes = self.model.names
        log.info("loaded %s with %d classes: %s", weights, len(self.classes), list(self.classes.values()))

    def detect(self, frame, conf: float | None = None) -> list[dict[str, Any]]:
        """Run inference on a single BGR frame.

        Returns a list of {label, confidence, box} dicts above the threshold,
        sorted most-confident first.
        """
        threshold = settings.CONFIDENCE if conf is None else conf
        results = self.model.predict(
            frame, imgsz=settings.IMG_SIZE, conf=threshold, verbose=False
        )
        if not results:
            return []

        found: list[dict[str, Any]] = []
        for box in results[0].boxes:
            cls_id = int(box.cls)
            found.append(
                {
                    "label": self.classes.get(cls_id, str(cls_id)),
                    "confidence": round(float(box.conf) * 100, 2),
                    "box": [round(float(v)) for v in box.xyxy[0]],
                }
            )
        found.sort(key=lambda d: d["confidence"], reverse=True)
        return found

    def is_weapon_model(self) -> bool:
        names = {str(v).lower() for v in self.classes.values()}
        return bool(names & {"knife", "pistol", "gun", "weapon"})
