#!/usr/bin/env python3
import sys

from pathlib import Path
from tqdm import tqdm

import pandas as pd

from dataclasses import dataclass

RESULT_FOLDER = Path("results")


@dataclass
class Experiment:
    dataset: str
    distance: str
    linkage: str
    strategy: str = "serial"


def parse_experiment_name(f):
    parts = f.stem.split("-")
    return Experiment(*parts)


def save(df, overwrite=False):
    file = RESULT_FOLDER / "results.csv"
    if not file.exists() or overwrite:
        df.to_csv(RESULT_FOLDER / "results.csv", index=False)
    else:
        print("  File already exists, adding new results to end.", file=sys.stderr)
        df_old = pd.read_csv(file)
        df_new = pd.concat([df_old, df], ignore_index=True)
        df_new.to_csv(file, index=False)
        df = df_new
    return df


def main():
    experiments = [f for f in RESULT_FOLDER.iterdir() if f.is_file() and f.name.startswith("results-")]
    print(
        f"Processing results from {len(experiments)} experiments ...", file=sys.stderr
    )
    dfs = []
    for file in tqdm(experiments, desc="Collecting results", file=sys.stderr):
        dfs.append(pd.read_csv(file))
    df = pd.concat(dfs, ignore_index=True)
    df = df.sort_values(by=["dataset", "distance", "linkage"])
    save(df)
    print("... done.", file=sys.stderr)


if __name__ == "__main__":
    main()
