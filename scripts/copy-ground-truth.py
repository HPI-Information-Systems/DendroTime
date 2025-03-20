#!/usr/bin/env python
from pathlib import Path
import shutil


def parse_folder_name(name):
    parts = name.split("-")
    assert len(parts) >= 4
    return parts[0], parts[1], parts[2]


def main():
    target_dataset = "Crop"
    target_distance = None
    target_linkage = None
    # assume, we are in the experiments directory
    cwd = Path.cwd()
    if cwd.name != "experiments":
        raise ValueError("This script should be run from the experiments directory!")

    result_folder = (cwd / "01-serial-hac" / "results").resolve()
    ground_truth_folder = (cwd / "data" / "ground-truth").resolve()
    print(f"Copying ground truth from {result_folder} to {ground_truth_folder}")

    for exp_folder in result_folder.iterdir():
        if exp_folder.is_file():
            continue

        # skip files
        if not exp_folder.is_dir():
            continue

        dataset, distance, linkage = parse_folder_name(exp_folder.name)
        if target_dataset is not None and dataset != target_dataset:
            continue
        if target_distance is not None and distance != target_distance:
            continue
        if target_linkage is not None and linkage != target_linkage:
            continue

        path = exp_folder / "serial" / "hierarchy.csv"
        if not path.exists() or not path.is_file():
            print(f"No hierarchy found for {dataset} - {distance} - {linkage}!")
            continue

        # copy result to ground-truth folder
        target_path = ground_truth_folder / dataset / f"hierarchy-{distance}-{linkage}.csv"
        target_path.parent.mkdir(parents=True, exist_ok=True)
        print(f"Copying {path} to {target_path}")
        shutil.copy(path, target_path)


if __name__ == "__main__":
    main()
