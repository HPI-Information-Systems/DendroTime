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



def load_quality_trace(strategy, dataset, distance, linkage):
    df = pd.read_csv(
        RESULT_FOLDER / f"{dataset}-{distance}-{linkage}-{strategy}" / "Finished-100" / "qualities.csv"
    )
    # use relative runtime instead of millis since epoch
    df["timestamp"] = df["timestamp"] - df.loc[0, "timestamp"]
    # convert millis to seconds
    df["timestamp"] = df["timestamp"] / 1000
    df["index"] = df["index"].astype(int)

    return df


def runtime_at_quality(strategy, dataset, distance, linkage, threshold, measure):
    try:
        df = load_quality_trace(strategy, dataset, distance, linkage)
        return df.loc[df[measure] >= threshold, "timestamp"].iloc[0]
    except (FileNotFoundError, KeyError) as e:
        print(
            f"Quality trace for {strategy} - {dataset}-{distance}-{linkage} not found: {e}"
        )
        return pd.NA


def main(threshold=0.8):
    experiments = [f for f in RESULT_FOLDER.iterdir() if f.is_dir()]
    print(
        f"Processing results from {len(experiments)} experiments ...", file=sys.stderr
    )
    entries = []
    for file in tqdm(experiments):
        exp = parse_experiment_name(file)
        exp_runtimes = pd.read_csv(file / "Finished-100" / "runtimes.csv")
        exp_runtimes["phase"] = exp_runtimes["phase"].str.lower()
        series = exp_runtimes.set_index("phase")["runtime"]
        series["runtime_80"] = runtime_at_quality(
            exp.strategy, exp.dataset, exp.distance, exp.linkage, threshold, "hierarchy-quality"
        )
        series["dataset"] = exp.dataset
        series["distance"] = exp.distance
        series["linkage"] = exp.linkage
        series["strategy"] = exp.strategy
        entries.append(series)
    df = pd.DataFrame(entries)
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
