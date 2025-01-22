#!/usr/bin/env python3
import sys
import shutil
from pathlib import Path


def main(result_folder, ground_truth_folder):
    result_folder = result_folder.resolve()
    ground_truth_folder = ground_truth_folder.resolve()

    for folder in result_folder.iterdir():
        if not folder.is_dir():
            continue

        hierarchy_file = folder / "serial" / "hierarchy.csv"
        if not hierarchy_file.exists():
            print(f"Hierarchy {hierarchy_file} does not exist")
            continue

        # parse name
        name_parts = folder.name.split("-")
        dataset = name_parts[0]
        distance = name_parts[1]
        linkage = name_parts[2]

        target_folder = ground_truth_folder / dataset
        gt_file = target_folder / f"hierarchy-{distance}-{linkage}.csv"

        print(
            f"Copying {hierarchy_file.relative_to(result_folder)} to "
            f"{gt_file.relative_to(ground_truth_folder)}"
        )
        target_folder.mkdir(exist_ok=True)
        shutil.copy2(hierarchy_file, gt_file)


if __name__ == "__main__":
    args = sys.argv[1:]
    main(Path(args[0]), Path(args[1]))
