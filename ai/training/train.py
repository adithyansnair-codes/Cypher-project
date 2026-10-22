#!/usr/bin/env python3
"""Train the CYPHER detector properly on the merged dataset.

Contrast with the first run (which produced ai/models/weapon_best.pt):

    first run            this run
    ---------            --------
    8 epochs             100 epochs (early stop, patience 20)
    27% of the data      100% of three datasets (~12k images)
    imgsz 416            imgsz 640
    yolov8n (3.2M)       yolov8s (11.2M)
    knife, pistol        knife, pistol, person, fire, smoke

Run:
    python ai/training/train.py            # ~1.5-3 h on an M4 Pro

Weights land in .dsh-scratch/training/runs/<name>/weights/best.pt
"""
from __future__ import annotations

import argparse
import os
import time
from pathlib import Path

from ultralytics import YOLO

DATASET = Path("/Users/adithyansnair/CYPHER/.dsh-scratch/datasets/cypher-v2/data.yaml")
RUNS = Path("/Users/adithyansnair/CYPHER/.dsh-scratch/training/runs")
BASE_WEIGHTS = Path("/Users/adithyansnair/CYPHER/.dsh-scratch/training/weights/yolov8s.pt")
RUN_NAME = "cypher-v2-yolov8s"

# Measured on this machine: ~8 min/epoch for 11.2k images at 640 px, batch 24
# on MPS (1.1 s/iteration, GPU waiting on the mosaic dataloader). So the budget
# is set for an overnight run rather than the 100 epochs that would take 14 h.
EPOCHS = 45
IMGSZ = 640
BATCH = 24
PATIENCE = 12
SAVE_EVERY = 5        # a usable checkpoint even if the run is interrupted


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=str(BASE_WEIGHTS),
                        help="starting weights (COCO yolov8s, or a previous run's checkpoint)")
    parser.add_argument("--name", default=RUN_NAME)
    parser.add_argument("--epochs", type=int, default=EPOCHS)
    parser.add_argument("--imgsz", type=int, default=IMGSZ,
                        help="640 for the first runs; 800 helps small objects on a 720p camera")
    parser.add_argument("--batch", type=int, default=BATCH)
    parser.add_argument("--lr0", type=float, default=0.01,
                        help="starting LR; use ~0.003 when continuing from a trained checkpoint")
    parser.add_argument("--patience", type=int, default=PATIENCE)
    args = parser.parse_args()

    if not DATASET.exists():
        raise SystemExit(f"dataset not built yet: {DATASET}\nrun ai/training/to_yolo.py first")

    # Ultralytics resolves relative paths against the working directory, and a
    # launchd job starts in "/" - which is read-only here, so the pretrained
    # download failed. Absolute paths and a writable cwd fix that.
    RUNS.mkdir(parents=True, exist_ok=True)
    os.chdir(RUNS)
    model = YOLO(args.base)             # transfer learning
    started = time.time()
    print(f"training {args.name} from {args.base}: {args.epochs} epochs, "
          f"imgsz={args.imgsz}, batch={args.batch}, lr0={args.lr0}, device=mps", flush=True)

    model.train(
        data=str(DATASET),
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        patience=args.patience,
        lr0=args.lr0,
        device="mps",
        workers=8,
        amp=False,                      # not supported on MPS
        cache=False,                    # 12k images would not fit comfortably in RAM
        cos_lr=True,
        close_mosaic=10,                # last 10 epochs without mosaic, steadier boxes
        save_period=SAVE_EVERY,
        seed=42,
        val=True,
        plots=True,
        project=str(RUNS),
        name=args.name,
        exist_ok=True,
        verbose=True,
    )

    print(f"\nDONE in {(time.time() - started) / 60:.1f} min", flush=True)
    best = RUNS / args.name / "weights" / "best.pt"
    print(f"best weights: {best}", flush=True)


if __name__ == "__main__":
    main()
