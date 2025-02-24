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
    df["index"] = df["index"].astype(int)

    return df


def runtime_at_quality(strategy, dataset, distance, linkage, thresholds, measure):
    try:
        df = load_quality_trace(strategy, dataset, distance, linkage)
        bins = {}
        for t in thresholds:
            try:
                bins[t] = df.loc[df[measure] >= t, "timestamp"].iloc[0]
            except (IndexError, KeyError) as e:
                bins[t] = pd.NA
        return bins
    except FileNotFoundError as e:
        print(
            f"Quality trace for {strategy} - {dataset}-{distance}-{linkage} not found: {e}"
        )
        return {t: pd.NA for t in thresholds}


def main():
    thresholds = [0.1*i for i in range(1, 10)]
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
        series["dataset"] = exp.dataset
        series["distance"] = exp.distance
        series["linkage"] = exp.linkage
        series["strategy"] = exp.strategy
        runtimes = runtime_at_quality(
            exp.strategy, exp.dataset, exp.distance, exp.linkage, thresholds, "hierarchy-quality"
        )
        for t in thresholds:
            series[f"runtime_{t:.1d}"] = runtimes[t]
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
