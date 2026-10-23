#!/usr/bin/env python3
"""Score a detector on the fixed comparison set and write the evidence.

Both the old and the new model are evaluated on the SAME 385-image guns-knives
test set, so the before/after numbers mean something. The new model is also
scored on the merged validation split, which is the only place person / fire /
smoke are measured.

    python ai/training/evaluate.py <weights> <output-dir>
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

import yaml
from ultralytics import YOLO

DATASETS = {
    "comparison-test": Path(
        "/Users/adithyansnair/CYPHER/.dsh-scratch/datasets/cypher-v2/data-test.yaml"),
    "merged-val": Path(
        "/Users/adithyansnair/CYPHER/.dsh-scratch/datasets/cypher-v2/data.yaml"),
}


def image_count(data_yaml: Path, split: str = "val") -> int:
    """Count images without relying on an internal Ultralytics counter."""
    config = yaml.safe_load(data_yaml.read_text())
    root = Path(config.get("path", data_yaml.parent))
    if not root.is_absolute():
        root = data_yaml.parent / root
    split_path = Path(config[split])
    if not split_path.is_absolute():
        split_path = root / split_path
    suffixes = {".bmp", ".jpeg", ".jpg", ".png", ".tif", ".tiff", ".webp"}
    return sum(1 for path in split_path.rglob("*") if path.suffix.lower() in suffixes)


def main() -> None:
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    weights = Path(sys.argv[1])
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)

    model = YOLO(str(weights))
    summary: dict[str, dict] = {}

    for label, data in DATASETS.items():
        if not data.exists():
            continue
        metrics = model.val(data=str(data), split="val", imgsz=640, device="mps",
                            plots=True, project=str(out), name=label, exist_ok=True)

        per_class = {}
        # ap_class_index lists the classes that actually have ground truth in
        # this split; metrics arrays are sized to that, not to len(names).
        for position, class_index in enumerate(metrics.box.ap_class_index):
            name = metrics.names[int(class_index)]
            per_class[name] = {
                "precision": round(float(metrics.box.p[position]), 4),
                "recall": round(float(metrics.box.r[position]), 4),
                "ap50": round(float(metrics.box.ap50[position]), 4),
                "ap50_95": round(float(metrics.box.ap[position]), 4),
            }

        summary[label] = {
            "images": image_count(data),
            "map50": round(float(metrics.box.map50), 4),
            "map50_95": round(float(metrics.box.map), 4),
            "mean_precision": round(float(metrics.box.mp), 4),
            "mean_recall": round(float(metrics.box.mr), 4),
            "per_class": per_class,
        }

        plots = (
            "confusion_matrix.png",
            "confusion_matrix_normalized.png",
            "BoxPR_curve.png",
            "BoxF1_curve.png",
            "BoxP_curve.png",
            "BoxR_curve.png",
        )
        for plot in plots:
            source = Path(metrics.save_dir) / plot
            if source.exists():
                shutil.copy(source, out / f"{label}-{plot}")

    (out / "metrics.json").write_text(json.dumps(
        {"weights": weights.name, "results": summary}, indent=2))

    lines = [f"# Evaluation — `{weights.name}`", ""]
    for label, values in summary.items():
        lines += [f"## {label} ({values['images']} images)", "",
                  "| Metric | Value |", "|---|---|",
                  f"| mAP@50 | **{values['map50']}** |",
                  f"| mAP@50-95 | {values['map50_95']} |",
                  f"| Mean precision | {values['mean_precision']} |",
                  f"| Mean recall | {values['mean_recall']} |", "",
                  "| Class | Precision | Recall | AP@50 | AP@50-95 |", "|---|---|---|---|---|"]
        for name, row in values["per_class"].items():
            lines.append(f"| {name} | {row['precision']} | {row['recall']} | {row['ap50']} | {row['ap50_95']} |")
        lines.append("")
    (out / "metrics.md").write_text("\n".join(lines))

    print(json.dumps(summary, indent=2), flush=True)
    print(f"\nwritten to {out}", flush=True)


if __name__ == "__main__":
    main()
