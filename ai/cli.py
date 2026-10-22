#!/usr/bin/env python3
"""CYPHER ML from the terminal.

Runs the trained detector on an image, a video, the Mac webcam, or the
ESP32-CAM's MJPEG stream, prints what it sees, and pushes incidents to the
gateway so they appear in the cockpit.

    # one photo, no side effects
    python ai/cli.py --source photo.jpg --dry-run

    # one photo -> incident in the cockpit
    python ai/cli.py --source photo.jpg --camera-id 1

    # Mac webcam for 30 s
    python ai/cli.py --source 0 --seconds 30

    # ESP32-CAM while the Mac is on CYPHER-CAM
    python ai/cli.py --source http://192.168.4.1:81/stream --seconds 60

Defaults: the six-class model from the last training run, 640 px, 0.35
confidence, 8-frame confirmation, 90 s cooldown. The gateway is the same
service the cockpit uses; it owns severity grading, not this script.
"""
from __future__ import annotations

import argparse
import sys
import time
from collections import defaultdict
from pathlib import Path

import cv2
from ultralytics import YOLO

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

SCRATCH = ROOT.parent

from ai.app.config import settings          # noqa: E402
from ai.app.gateway import GatewayClient    # noqa: E402

DEFAULT_WEIGHTS = SCRATCH / "training/runs/cypher-v3-6class/weights/best.pt"

CRITICAL_OBJECTS = {"weapon", "gun", "pistol", "rifle", "knife", "fire"}


def explain(label: str, confidence: float) -> str:
    """Mirror of the gateway's rule, only for printing why a severity happened."""
    if label in CRITICAL_OBJECTS:
        return f"'{label}' is a high-risk class -> CRITICAL regardless of confidence"
    if label in {"smoke", "fall", "lying"}:
        return (f"'{label}' at {confidence:.0f}% -> "
                f"{'HIGH' if confidence >= 85 else 'MEDIUM'} (health/fire rule)")
    if confidence >= 90:
        return f"confidence {confidence:.0f}% >= 90% -> HIGH"
    if confidence >= 70:
        return f"confidence {confidence:.0f}% >= 70% -> MEDIUM"
    return f"confidence {confidence:.0f}% < 70% -> LOW"


def reportable(label: str) -> bool:
    return label in CRITICAL_OBJECTS or label in {"smoke", "fall", "person", "lying"}


def open_source(source: str) -> cv2.VideoCapture | None:
    src = int(source) if source.isdigit() else source
    cap = cv2.VideoCapture(src)
    return cap if cap.isOpened() else None


def best_per_class(result) -> dict[str, dict]:
    """One detection per class per frame -- the same rule the AI engine uses."""
    names = result.names
    best: dict[str, dict] = {}
    for box in result.boxes:
        label = str(names[int(box.cls)]).lower()
        conf = round(float(box.conf) * 100, 2)
        if label not in best or conf > best[label]["confidence"]:
            best[label] = {
                "label": label,
                "confidence": conf,
                "box": [int(v) for v in box.xyxy[0].tolist()],
            }
    return best


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", default="0",
                        help="image/video path, camera index (0), or MJPEG URL")
    parser.add_argument("--weights", default=str(DEFAULT_WEIGHTS))
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--gateway", default=settings.GATEWAY_URL)
    parser.add_argument("--conf", type=float, default=0.35)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--confirm", type=int, default=8, help="frames before reporting")
    parser.add_argument("--cooldown", type=float, default=90.0, help="seconds per class")
    parser.add_argument("--seconds", type=float, default=30.0, help="max stream duration")
    parser.add_argument("--stride", type=int, default=1, help="process every Nth frame")
    parser.add_argument("--dry-run", action="store_true", help="never post to the gateway")
    parser.add_argument("--save", type=str, default=None,
                        help="directory for annotated frames")
    args = parser.parse_args()

    if not Path(args.weights).exists():
        raise SystemExit(f"weights not found: {args.weights}")

    settings.GATEWAY_URL = args.gateway
    model = YOLO(args.weights)
    gateway = GatewayClient()
    classes = ", ".join(str(v) for v in model.names.values())

    print("=" * 78)
    print("CYPHER ML · terminal pipeline")
    print(f"weights   {Path(args.weights).name}")
    print(f"classes   {classes}")
    print(f"source    {args.source}")
    print(f"gateway   {args.gateway}{'  (dry run)' if args.dry_run else ''}")
    print("=" * 78, flush=True)

    if args.save:
        Path(args.save).mkdir(parents=True, exist_ok=True)

    single_image = (not args.source.startswith("http")
                    and Path(args.source).suffix.lower() in {".jpg", ".jpeg", ".png", ".bmp"})
    if single_image:
        frame = cv2.imread(args.source)
        if frame is None:
            raise SystemExit(f"could not read image: {args.source}")
        frames = [(frame, True)]
    else:
        cap = open_source(args.source)
        if cap is None:
            raise SystemExit(f"could not open source: {args.source}")
        frames = None

    streak: dict[tuple[int, str], int] = defaultdict(int)
    last_raised: dict[tuple[int, str], float] = defaultdict(float)
    totals: dict[str, int] = defaultdict(int)
    raised = 0
    processed = 0
    started = time.time()

    def handle(frame, index: int) -> None:
        nonlocal raised, processed
        processed += 1
        result = model.predict(frame, imgsz=args.imgsz, conf=args.conf, verbose=False)[0]
        best = best_per_class(result)

        kept: dict[str, int] = {}
        for label in list(streak):
            if label[0] == args.camera_id and label[1] not in best:
                streak[label] = 0

        for label, det in sorted(best.items(), key=lambda kv: -kv[1]["confidence"]):
            totals[label] += 1
            if single_image:
                state = "report"
            else:
                streak[(args.camera_id, label)] += 1
                kept[label] = streak[(args.camera_id, label)]
                if kept[label] < args.confirm:
                    state = f"holding {kept[label]}/{args.confirm}"
                elif time.time() - last_raised[(args.camera_id, label)] < args.cooldown:
                    state = "cooldown"
                elif not reportable(label):
                    state = "not reportable"
                else:
                    state = "report"

            print(f"frame {index:>5} | {label:<7} {det['confidence']:5.1f}% "
                  f"box={det['box']} | {state}", flush=True)

            if state != "report":
                continue

            if args.dry_run:
                print(f"          would post: {label} -> {explain(label, det['confidence'])}",
                      flush=True)
                continue

            try:
                incident = gateway.report_incident(
                    title=f"{label.capitalize()} detected",
                    detected_object=label,
                    confidence=det["confidence"],
                    description=f"{label} detected by ai/cli.py",
                    camera_id=args.camera_id,
                    trace=[f"cli: {label} {det['confidence']:.1f}% in {det['box']}"],
                    model=Path(args.weights).name,
                    threshold=args.conf,
                )
                raised += 1
                last_raised[(args.camera_id, label)] = time.time()
                print(f"          INCIDENT #{incident.get('incidentId')} "
                      f"{incident.get('severity')} | {explain(label, det['confidence'])}",
                      flush=True)
            except Exception as exc:  # noqa: BLE001 - keep the loop alive
                print(f"          POST FAILED: {exc}", flush=True)

        if args.save and best:
            for det in best.values():
                x1, y1, x2, y2 = det["box"]
                cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 0, 255), 2)
                cv2.putText(frame, f"{det['label']} {det['confidence']:.0f}%",
                            (x1, max(20, y1 - 8)), cv2.FONT_HERSHEY_SIMPLEX, 0.6,
                            (0, 0, 255), 2)
        if args.save:
            cv2.imwrite(str(Path(args.save) / f"frame-{index:06d}.jpg"), frame)

    if frames is not None:
        for index, (frame, _) in enumerate(frames, start=1):
            handle(frame, index)
    else:
        index = 0
        while time.time() - started < args.seconds:
            ok, frame = cap.read()
            if not ok:
                break
            index += 1
            if index % max(1, args.stride) != 0:
                continue
            handle(frame, index)
        cap.release()
        if index == 0:
            raise SystemExit("source opened but returned no frames")

    elapsed = time.time() - started
    print("-" * 78)
    print(f"processed {processed} frame(s) in {elapsed:.1f}s · "
          f"incidents raised: {raised}")
    if totals:
        print("detections by class: " +
              ", ".join(f"{k}={v}" for k, v in sorted(totals.items())))
    print("-" * 78)


if __name__ == "__main__":
    main()
