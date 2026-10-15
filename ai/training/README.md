# ai/training — how the detector is trained

The first detector (`ai/models/weapon_best.pt`) was a deadline run: **8 epochs on
27 % of one dataset at 416 px**. It scored mAP@50 0.589 with knife recall 0.345,
and on a live webcam it looked bad for a reason that is now measured: **every
image in that dataset is exactly 300×300 px** (10 KB web thumbnails), so the
model had never seen a 1280×720 frame.

This directory rebuilds it properly.

## Datasets

| Source | Classes | Images | Resolution | Provenance |
|---|---|---|---|---|
| `guns-knives-yolo` (local) | knife, pistol | 4,409 / 1,043 / 385 | 300×300 | Kaggle "guns-knives object detection" |
| `Subh775/WeaponDetection_Grouped` | gun → **pistol**, knife, person | ~7,300 | 416–1024 | Hugging Face, CC-BY style Roboflow export |
| `KeenForgeAI/DBA_Fire_Smoke-corrected` | fire, smoke | ~7,700 | mixed | Hugging Face, Pascal VOC (license stated as "see upstream") |

Two notes on honesty: the HF weapon set's own train/validation split is badly
skewed for `person` (295 boxes train vs 973 val), so `to_yolo.py` re-splits it by
a hash of the filename; and `GUN` becomes our `pistol` class because they are
the same object in practice — a second near-identical class would only split the
learning signal.

## Pipeline

```bash
python ai/training/fetch_datasets.py     # downloads the two HF sets (~1.1 GB)
python ai/training/to_yolo.py            # merges everything into one YOLO dataset
python ai/training/train.py              # fine-tunes yolov8s (overnight)
python ai/training/evaluate.py <weights> <out-dir>
```

Everything lands **outside the repository**, in `../.dsh-scratch/`:

```
.dsh-scratch/datasets/hf/          raw downloads
.dsh-scratch/datasets/cypher-v2/   merged YOLO dataset + data.yaml + stats.json
.dsh-scratch/training/runs/        training runs, weights, curves
```

The merged set is 11,264 train / 1,803 val images over five classes
(`knife, pistol, person, fire, smoke`) — classes 3 and 4 stay empty until the
fire/smoke download completes, deliberately: keeping the indices stable means a
follow-up fine-tune can add them without touching the label files.

## The comparison that matters

`to_yolo.py` copies the original 385-image test set into the new dataset
untouched, and `evaluate.py` scores any weights on it. So the old model and the
new one are measured on exactly the same images, and the before/after table is
honest.

## Hardware notes

Measured on the M4 Pro (24 GB, MPS): inference is 13 ms/frame at 416 px and
18 ms at 640 px — speed was never the problem. **Training** is the slow part:
~9 min per epoch at 640 px, batch 24, because the mosaic dataloader saturates
the CPU while the GPU waits. 45 epochs is an overnight run, hence
`save_period=5` and `patience=12`.
