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


def main():
    experiments = [f for f in RESULT_FOLDER.iterdir() if f.is_dir()]
    print(
        f"Processing results from {len(experiments)} experiments ...", file=sys.stderr
    )
    entries = []
    for file in tqdm(experiments):
        exp = parse_experiment_name(file)
        exp_runtimes = pd.read_csv(file / "serial" / "runtimes.csv")
        for phase, runtime in exp_runtimes.itertuples(index=False):
            entries.append(
                (exp.dataset, exp.distance, exp.linkage, exp.strategy, phase, runtime)
            )
    df = pd.DataFrame(
        entries,
        columns=["dataset", "distance", "linkage", "strategy", "phase", "runtime"],
    )
    file = RESULT_FOLDER / "aggregated-runtimes.csv"
    if not file.exists():
        df.to_csv(RESULT_FOLDER / "aggregated-runtimes.csv", index=False)
    else:
        print("  File already exists, adding new results to end.", file=sys.stderr)
        df_old = pd.read_csv(file)
        df_new = pd.concat([df_old, df], ignore_index=True)
        df_new.to_csv(file, index=False)
    print("... done.", file=sys.stderr)


if __name__ == "__main__":
    main()
