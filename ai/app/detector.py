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
        self.person_model = (
            YOLO(settings.PERSON_WEIGHTS)
            if Path(settings.PERSON_WEIGHTS).exists()
            and Path(settings.PERSON_WEIGHTS) != Path(weights)
            else None
        )
        self.weights = weights
        self.classes = self.model.names
        log.info("loaded %s with %d classes: %s", weights, len(self.classes), list(self.classes.values()))

    def detect(
        self,
        frame,
        conf: float | None = None,
        webcam: bool = False,
    ) -> list[dict[str, Any]]:
        """Run inference on a single BGR frame.

        Returns a list of {label, confidence, box} dicts above the threshold,
        sorted most-confident first.
        """
        threshold = settings.CONFIDENCE if conf is None else conf
        found = self._predict(frame, threshold)

        # A wide webcam frame shrinks a hand-held knife to a very small region.
        # The full-frame pass can miss it even though the model detects it when
        # the same crop is enlarged to the network's 640 px input. Scan two
        # overlapping middle crops for reportable weapons and map their boxes
        # back into full-frame coordinates.
        weapons = {"knife", "pistol", "gun", "weapon", "rifle"}
        if webcam and not any(d["label"] in weapons for d in found):
            height, width = frame.shape[:2]
            regions = (
                ("left", 0.22, 0.08, 0.72, 1.00),
                ("right", 0.28, 0.08, 0.78, 1.00),
                ("centre", 0.18, 0.05, 0.82, 0.98),
            )
            for region_name, x0f, y0f, x1f, y1f in regions:
                x0, y0 = int(width * x0f), int(height * y0f)
                x1, y1 = int(width * x1f), int(height * y1f)
                if x1 <= x0 or y1 <= y0:
                    continue
                for detection in self._predict(frame[y0:y1, x0:x1], threshold):
                    if detection["label"] not in weapons:
                        continue
                    bx1, by1, bx2, by2 = detection["box"]
                    detection["box"] = [
                        bx1 + x0, by1 + y0, bx2 + x0, by2 + y0
                    ]
                    detection["region"] = region_name
                    found.append(detection)

        # COCO YOLOv8s is a strong general person detector. The six-class
        # checkpoint is better for weapons/fire, but a close webcam face/torso
        # is sometimes outside its person training distribution. Add the COCO
        # result and replace a weaker custom person box for webcam frames.
        if webcam and self.person_model is not None:
            person_result = self.person_model.predict(
                frame, imgsz=settings.IMG_SIZE, conf=0.25, verbose=False
            )[0]
            people = []
            for box in person_result.boxes:
                if person_result.names[int(box.cls)] != "person":
                    continue
                people.append({
                    "label": "person",
                    "confidence": round(float(box.conf) * 100, 2),
                    "box": [round(float(v)) for v in box.xyxy[0]],
                    "region": "full",
                })
            if people:
                found = [d for d in found if d["label"] != "person"] + people

        found.sort(key=lambda d: d["confidence"], reverse=True)
        return found

    def _predict(self, frame, threshold: float) -> list[dict[str, Any]]:
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
