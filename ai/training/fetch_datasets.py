#!/usr/bin/env python3
"""Fetch the additional training data from Hugging Face.

Two public datasets, no credentials required:

  Subh775/WeaponDetection_Grouped        gun / knife / person, 416-1024 px
  KeenForgeAI/DBA_Fire_Smoke-corrected   fire / smoke, Pascal VOC, ~7.7k images

The existing guns-knives-yolo set (300x300, knife / pistol) is already on disk
and is merged in by to_yolo.py.

Data is downloaded OUTSIDE the repository - .dsh-scratch/datasets - because it
is hundreds of megabytes and has no business being in git.
"""
from pathlib import Path

from huggingface_hub import snapshot_download

ROOT = Path("/Users/adithyansnair/CYPHER/.dsh-scratch/datasets/hf")

TARGETS = {
    "weapongrouped": "Subh775/WeaponDetection_Grouped",
    "dba_fire_smoke": "KeenForgeAI/DBA_Fire_Smoke-corrected",
}


def main() -> None:
    for name, repo in TARGETS.items():
        destination = ROOT / name
        destination.mkdir(parents=True, exist_ok=True)
        print(f"==> {repo} -> {destination}", flush=True)
        snapshot_download(
            repo_id=repo,
            repo_type="dataset",
            local_dir=str(destination),
            max_workers=4,
        )
        print(f"    done {repo}", flush=True)

    print("ALL DONE", flush=True)


if __name__ == "__main__":
    main()
