#!/usr/bin/env python3
"""Fetch the additional training data from Hugging Face.

Four public datasets, no credentials required:

  weapongrouped    Subh775/WeaponDetection_Grouped        gun / knife / person
  dba_fire_smoke   KeenForgeAI/DBA_Fire_Smoke-corrected   fire / smoke (VOC)
  simuletic_fall   Simuletic/CCTV_..._Fall_Lying_Down     lying / fallen, overhead
  crowdhuman_val   Funtion/CrowdHuman                     person boxes (val split)

The existing guns-knives-yolo set (300x300, knife / pistol) is already on disk
and is merged in by to_yolo.py.

Pass names to fetch a subset:  fetch_datasets.py simuletic_fall crowdhuman_val

Data is downloaded OUTSIDE the repository - .dsh-scratch/datasets - because it
is hundreds of megabytes and has no business being in git.
"""
from pathlib import Path

from huggingface_hub import snapshot_download

ROOT = Path("/Users/adithyansnair/CYPHER/.dsh-scratch/datasets/hf")

# name -> (repo, allow_patterns or None)
TARGETS = {
    "weapongrouped": ("Subh775/WeaponDetection_Grouped", None),
    "dba_fire_smoke": ("KeenForgeAI/DBA_Fire_Smoke-corrected", None),
    "simuletic_fall": ("Simuletic/CCTV_Incident_Dataset_Fall_Lying_Down_Detection", None),
    # CrowdHuman is 14 GB in total; the validation split alone is ~1.5 GB and
    # carries ~99k person boxes, which is what our thin `person` class needs.
    "crowdhuman_val": ("Funtion/CrowdHuman", ["CrowdHuman_val.zip", "annotation_val.odgt"]),
}


def main() -> None:
    import sys

    wanted = set(sys.argv[1:]) or set(TARGETS)
    for name, (repo, patterns) in TARGETS.items():
        if name not in wanted:
            continue
        destination = ROOT / name
        destination.mkdir(parents=True, exist_ok=True)
        print(f"==> {repo} -> {destination}", flush=True)
        snapshot_download(
            repo_id=repo,
            repo_type="dataset",
            local_dir=str(destination),
            max_workers=4,
            allow_patterns=patterns,
        )
        print(f"    done {repo}", flush=True)

    print("ALL DONE", flush=True)


if __name__ == "__main__":
    main()
