# ai/training — how the CYPHER detector is trained

The first detector (`ai/models/weapon_best.pt`) was a deadline prototype:
**8 epochs on 27% of one dataset at 416 px**. It scored mAP@50 0.589 with knife
recall 0.345. Every image in that source was 300×300, so the model had never
seen a 1280×720 camera frame.

This directory builds the current detector: YOLOv8s at 640 px, six classes,
multiple sources, a deterministic split and a fixed held-out comparison set.

## Final dataset

| Source | Contribution | Classes |
|---|---|---|
| Kaggle `guns-knives-yolo` | Local Roboflow export; original 385-image test split is preserved | knife, pistol |
| `Subh775/WeaponDetection_Grouped` | Hugging Face parquet export, converted from absolute boxes | gun → pistol, knife, person |
| `KeenForgeAI/DBA_Fire_Smoke-corrected` | Pascal VOC annotations converted to YOLO | flame → fire, smoke |
| `Simuletic/...Fall_Lying_Down_Detection` | Pose annotations reduced to detection boxes | laying → lying, standing → person |
| `Funtion/CrowdHuman` | CrowdHuman validation source used to strengthen person detection | person |

Final split: **18,642 train / 2,656 validation / 385 held-out comparison
images**, with approximately 13.3k validation boxes.

Two dataset decisions matter:

- The Hugging Face weapon set's published split was badly skewed for `person`
  (295 train boxes versus 973 validation boxes). `to_yolo.py` re-splits all
  three source parquet files by a deterministic hash of the filename.
- `GUN` becomes `pistol` because both labels describe handguns in these sources.
  Keeping two near-identical classes would split the same learning signal and
  create duplicate incidents.

## Labels and counts

The class order is stable so future fine-tunes do not need their label files
rewritten:

| Index | Class | Train boxes | Validation boxes |
|---:|---|---:|---:|
| 0 | knife | 3,130 | 662 |
| 1 | pistol | 10,574 | 1,492 |
| 2 | person | 94,069 | 10,324 |
| 3 | fire | 4,673 | 538 |
| 4 | smoke | 2,511 | 266 |
| 5 | lying | 96 | 15 |

`lying` is deliberately flagged as a sparse class: 96 training boxes are enough
to demonstrate the end-to-end path, not enough to claim a production-grade fall
detector.

## Training schedule

The current checkpoint was trained in two stages on an Apple M4 Pro:

| Stage | Start | Epochs | Input | Result |
|---|---|---:|---:|---|
| 1 | COCO `yolov8s.pt` | 40 | 640 px | `stage1-5class-40ep.pt` |
| 2 | stage-1 checkpoint | 20 | 640 px | `cypher-v3-6class/weights/best.pt` |

Stage 2 uses batch 24, optimizer auto-selection, cosine LR with `lr0=0.01`,
`close_mosaic=10`, seed 42 and MPS. The second stage adds `lying` while
retaining the five classes learned in stage 1.

The later `cypher-v4-800` experiment started from the v3 checkpoint at 800 px.
It improved some small-object scores but was not adopted as the main demo
checkpoint.

## Commands

Everything generated lives outside Git because the raw data is roughly 1 GB and
the stage-1 checkpoint is 85 MB:

```bash
cd ~/CYPHER/.dsh-scratch/work

python ai/training/fetch_datasets.py   # downloads the Hugging Face sources
python ai/training/to_yolo.py          # merges and normalises the dataset
python ai/training/train.py            # configurable Ultralytics training
python ai/training/status.sh           # last epochs of the active run
```

To continue from the stage-1 checkpoint for the current 20-epoch stage:

```bash
~/CYPHER/.dsh-scratch/ml/bin/python ai/training/train.py \
  --base ~/CYPHER/.dsh-scratch/training/weights/stage1-5class-40ep.pt \
  --name cypher-v3-6class \
  --epochs 20 \
  --imgsz 640 \
  --batch 24
```

After training, generate the full evidence set:

```bash
python ai/training/evaluate.py \
  ~/CYPHER/.dsh-scratch/training/runs/cypher-v3-6class/weights/best.pt \
  ai/evidence
```

## The comparison that matters

`to_yolo.py` copies the original 385-image guns-knives test set into the merged
dataset untouched, and `evaluate.py` scores old and new weights on those exact
images. This produces the honest before/after result:

| Metric | Old prototype | Current model |
|---|---:|---:|
| mAP@50 | 0.589 | **0.8898** |
| mAP@50–95 | 0.403 | **0.6502** |
| Knife recall | 0.345 | **0.8426** |

The all-class merged-validation metrics, PR curves, confusion matrices and
known limitations are documented in [`../evidence/README.md`](../evidence/README.md).

## Hardware notes

Measured on the M4 Pro with MPS: inference is about 13 ms/frame at 416 px and
18 ms/frame at 640 px, so inference latency is acceptable for a demo. Training
is the expensive part because the CPU-side mosaic dataloader cannot always keep
the accelerator busy. The project therefore uses periodic checkpoints and
early stopping rather than promising a fixed number of hours for every run.
