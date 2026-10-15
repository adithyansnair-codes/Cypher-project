#!/usr/bin/env python3
"""Build one YOLO dataset from the three sources we have.

  guns-knives-yolo (local, 300x300)          knife, pistol        -> copied as-is
  Subh775/WeaponDetection_Grouped (parquet)  GUN, KNIFE, PERSON   -> remapped
  DBA_Fire_Smoke-corrected (Pascal VOC)      fire, smoke          -> converted

Why the remap: this project's priority vocabulary is knife / pistol / person /
fire / smoke, and "GUN" in the HF set means handguns, so it becomes `pistol`
rather than a second near-identical class.

Output (outside the repo, it is ~1 GB once converted):

  .dsh-scratch/datasets/cypher-v2/
    images/{train,val}  labels/{train,val}   data.yaml
    test/{images,labels}                     data-test.yaml
    stats.json

The original 385-image test set is copied untouched to `test/` so the new model
and the old one can be scored on exactly the same images.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

import pyarrow.parquet as pq
from PIL import Image

CLASSES = ["knife", "pistol", "person", "fire", "smoke"]

GUNS_KNIVES = Path("/Users/adithyansnair/Downloads/archive (3)/guns-knives-yolo/guns-knives-yolo")
HF_ROOT = Path("/Users/adithyansnair/CYPHER/.dsh-scratch/datasets/hf")
WEAPON_GROUPED = HF_ROOT / "weapongrouped"
DBA = HF_ROOT / "dba_fire_smoke"

OUT = Path("/Users/adithyansnair/CYPHER/.dsh-scratch/datasets/cypher-v2")

# Subh775 category index -> our class index
WEAPON_GROUPED_MAP = {0: 1, 1: 0, 2: 2}   # GUN->pistol, KNIFE->knife, PERSON->person
VOC_MAP = {"fire": 3, "smoke": 4}


def fresh_dir(path: Path) -> None:
    # Guard: only ever clear our own derived directory.
    assert path.name == "cypher-v2", f"refusing to clear {path}"
    if path.exists():
        shutil.rmtree(path)
    for sub in ("images/train", "images/val", "labels/train", "labels/val",
                "test/images", "test/labels"):
        (path / sub).mkdir(parents=True, exist_ok=True)


def write_label(path: Path, rows: list[str]) -> None:
    path.write_text("\n".join(rows) + ("\n" if rows else ""))


def normalise(box: tuple[float, float, float, float],
              width: int, height: int) -> tuple[float, float, float, float] | None:
    x, y, w, h = box
    if w <= 1 or h <= 1 or width <= 0 or height <= 0:
        return None
    cx = min(max((x + w / 2) / width, 0.0), 1.0)
    cy = min(max((y + h / 2) / height, 0.0), 1.0)
    nw = min(max(w / width, 0.0), 1.0)
    nh = min(max(h / height, 0.0), 1.0)
    return cx, cy, nw, nh


def add_guns_knives(stats: Counter) -> None:
    """Already YOLO format with the same class order for knife/pistol."""
    for split_in, split_out in (("train", "train"), ("valid", "val")):
        images = sorted((GUNS_KNIVES / split_in / "images").glob("*.jpg"))
        for image in images:
            label = GUNS_KNIVES / split_in / "labels" / (image.stem + ".txt")
            if not label.exists():
                continue
            rows = []
            for line in label.read_text().splitlines():
                parts = line.split()
                if len(parts) == 5:
                    rows.append(line)
                    stats[split_out][CLASSES[int(parts[0])]] += 1
            name = "gk_" + image.name
            shutil.copy(image, OUT / "images" / split_out / name)
            write_label(OUT / "labels" / split_out / (Path(name).stem + ".txt"), rows)

    for image in sorted((GUNS_KNIVES / "test" / "images").glob("*.jpg")):
        label = GUNS_KNIVES / "test" / "labels" / (image.stem + ".txt")
        if not label.exists():
            continue
        name = "gk_" + image.name
        shutil.copy(image, OUT / "test" / "images" / name)
        shutil.copy(label, OUT / "test" / "labels" / (Path(name).stem + ".txt"))


def add_weapon_grouped(stats: Counter) -> None:
    """Parquet with embedded JPEG bytes and absolute [x, y, w, h] boxes.

    The published train/validation split is badly skewed for `person` (295
    boxes in train against 973 in validation), so all three parquets are
    re-split here by a hash of the image name: deterministic, and every class
    ends up proportionally in both halves.
    """
    data_dir = WEAPON_GROUPED / "data"
    for parquet in sorted(data_dir.glob("*.parquet")):
        table = pq.read_table(parquet, columns=["image", "width", "height", "objects"])
        images = table.column("image").to_pylist()
        widths = table.column("width").to_pylist()
        heights = table.column("height").to_pylist()
        objects = table.column("objects").to_pylist()

        for index, (blob, width, height, found) in enumerate(
                zip(images, widths, heights, objects)):
            payload = blob.get("bytes") if isinstance(blob, dict) else blob
            if not payload:
                continue

            name = f"wg_{parquet.stem}_{index:06d}.jpg"
            split_out = "val" if _is_val(name) else "train"

            rows = []
            for box, category in zip(found.get("bbox") or [], found.get("category") or []):
                our_class = WEAPON_GROUPED_MAP.get(int(category))
                if our_class is None:
                    continue
                normalised = normalise(tuple(box), int(width), int(height))
                if normalised is None:
                    continue
                cx, cy, nw, nh = normalised
                rows.append(f"{our_class} {cx:.6f} {cy:.6f} {nw:.6f} {nh:.6f}")
                stats[split_out][CLASSES[our_class]] += 1
            if not rows:
                continue

            (OUT / "images" / split_out / name).write_bytes(payload)
            write_label(OUT / "labels" / split_out / (Path(name).stem + ".txt"), rows)


def add_dba_fire_smoke(stats: Counter) -> None:
    """Pascal VOC: 90/10 split by image-name hash, so it never shuffles."""
    annotations = DBA / "Annotations"
    images_dir = DBA / "JPEGImages"

    for xml in sorted(annotations.glob("*.xml")):
        image = None
        for suffix in (".jpg", ".jpeg", ".png"):
            candidate = images_dir / (xml.stem + suffix)
            if candidate.exists():
                image = candidate
                break
        if image is None:
            continue

        try:
            root = ET.parse(xml).getroot()
        except ET.ParseError:
            continue

        split_out = "val" if _is_val(xml.stem) else "train"
        size = root.find("size")
        width = int(float(size.findtext("width", "0")))
        height = int(float(size.findtext("height", "0")))
        if width <= 0 or height <= 0:
            # fall back to the image itself rather than dropping the sample
            with Image.open(image) as im:
                width, height = im.size

        rows = []
        for obj in root.findall("object"):
            our_class = VOC_MAP.get((obj.findtext("name") or "").strip().lower())
            if our_class is None:
                continue
            box = obj.find("bndbox")
            if box is None:
                continue
            x1 = float(box.findtext("xmin", "0"))
            y1 = float(box.findtext("ymin", "0"))
            x2 = float(box.findtext("xmax", "0"))
            y2 = float(box.findtext("ymax", "0"))
            normalised = normalise((x1, y1, x2 - x1, y2 - y1), width, height)
            if normalised is None:
                continue
            cx, cy, nw, nh = normalised
            rows.append(f"{our_class} {cx:.6f} {cy:.6f} {nw:.6f} {nh:.6f}")
            stats[split_out][CLASSES[our_class]] += 1
        if not rows:
            continue

        name = "dba_" + image.name
        shutil.copy(image, OUT / "images" / split_out / name)
        write_label(OUT / "labels" / split_out / (Path(name).stem + ".txt"), rows)


def _is_val(stem: str) -> bool:
    digest = hashlib.sha256(stem.encode()).hexdigest()
    return int(digest[:8], 16) % 10 == 0


def write_yaml() -> None:
    names = "\n".join(f"  {i}: {name}" for i, name in enumerate(CLASSES))
    (OUT / "data.yaml").write_text(
        f"path: {OUT}\ntrain: images/train\nval: images/val\nnc: {len(CLASSES)}\nnames:\n{names}\n")
    (OUT / "data-test.yaml").write_text(
        f"path: {OUT / 'test'}\ntrain: images/val\nval: images/val\n"
        f"nc: {len(CLASSES)}\nnames:\n{names}\n")


def main() -> None:
    fresh_dir(OUT)
    stats: Counter = Counter({"train": Counter(), "val": Counter()})

    print("==> guns-knives (knife, pistol)", flush=True)
    add_guns_knives(stats)
    if WEAPON_GROUPED.exists():
        print("==> weapon-grouped (gun/knife/person)", flush=True)
        add_weapon_grouped(stats)
    else:
        print("!! weapon-grouped not downloaded yet, skipping", flush=True)
    if DBA.exists():
        print("==> dba fire/smoke", flush=True)
        add_dba_fire_smoke(stats)
    else:
        print("!! dba fire/smoke not downloaded yet, skipping", flush=True)

    write_yaml()
    report = {
        "classes": CLASSES,
        "counts": {split: dict(counter) for split, counter in stats.items()},
        "images": {split: len(list((OUT / "images" / split).glob("*.jpg")))
                   for split in ("train", "val")},
        "test_images": len(list((OUT / "test" / "images").glob("*.jpg"))),
    }
    (OUT / "stats.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    main()
