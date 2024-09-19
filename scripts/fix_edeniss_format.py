#!/usr/bin/env python3

# This script is used to fix the format of the Eden ISS data. It will remove whitespace
# in the metadata section and the timestamps from the data section.
#
# The input folder should contain one to many .ts files with the Eden ISS data. The
# files will be copied to the output without modifications in the input folder.
#
# Dependencies:
# - aeon
# - numpy
#
# Usage:
# python fix_edeniss_format.py <input_folder> <output_folder>

import sys
import time
from pathlib import Path

import numpy as np
from aeon.datasets import load_from_tsfile, write_to_tsfile


def fix_edeniss_format(input_folder: Path, output_folder: Path) -> None:
    output_folder.mkdir(parents=True, exist_ok=True)

    for file in input_folder.glob("*.ts"):
        X, y, meta = load_from_tsfile(file, return_meta_data=True)
        problem_name = meta["problemname"].replace("-", "_")
        print(f"Saving {problem_name} to {output_folder}")
        write_to_tsfile(
            X,
            output_folder,
            y,
            problem_name=problem_name,
            regression=meta["targetlabel"],
        )


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python fix_edeniss_format.py <input_folder> <output_folder>")
        sys.exit(1)

    input_folder = Path(sys.argv[1])
    output_folder = Path(sys.argv[2])
    if not input_folder.exists():
        print(f"Input folder {input_folder} does not exist.")
        sys.exit(1)

    if input_folder == output_folder:
        print(
            "Input and output folder are equal, this will overwrite the input!\n"
            "Continuing in 5 seconds (use Ctrl+C to cancel)\n"
            "5"
        )

        for i in range(4, -1, -1):
            print(i)
            time.sleep(1)

    fix_edeniss_format(input_folder, output_folder)
