#!/usr/bin/env python3
import sys

from pathlib import Path
from tqdm import tqdm

import pandas as pd
import numpy as np

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


def assess_quality(strategy, dataset, distance, linkage, thresholds, measure, max_runtime, total_runtime):
    try:
        df = load_quality_trace(strategy, dataset, distance, linkage)
        # compute runtime for each threshold
        runtimes = {}
        for t in thresholds:
            try:
                runtimes[t] = df.loc[df[measure] >= t, "timestamp"].iloc[0]
            except (IndexError, KeyError) as e:
                runtimes[t] = total_runtime

        # compute WHS-R-AUC
        x = np.r_[df["timestamp"], max_runtime]
        y = np.r_[df[measure], 1]
        whs_runtime_auc = np.sum(y[:-1] * np.diff(x, 1)) / max_runtime
        return runtimes, whs_runtime_auc
    except (FileNotFoundError, KeyError) as e:
        print(
            f"Quality trace for {strategy} - {dataset}-{distance}-{linkage} not found: {e}"
        )
        return {t: total_runtime for t in thresholds}, np.nan


def save(df, overwrite=False):
    file = RESULT_FOLDER / "aggregated-runtimes.csv"
    if not file.exists() or overwrite:
        df.to_csv(RESULT_FOLDER / "aggregated-runtimes.csv", index=False)
    else:
        print("  File already exists, adding new results to end.", file=sys.stderr)
        df_old = pd.read_csv(file)
        df_new = pd.concat([df_old, df], ignore_index=True)
        df_new.to_csv(file, index=False)
        df = df_new
    return df


def main():
    thresholds = [0.1*i for i in range(1, 10)]
    experiments = [f for f in RESULT_FOLDER.iterdir() if f.is_dir()]
    print(
        f"Processing results from {len(experiments)} experiments ...", file=sys.stderr
    )
    entries = []
    for file in tqdm(experiments, desc="Collecting runtimes", file=sys.stderr):
        exp = parse_experiment_name(file)
        exp_runtimes = pd.read_csv(file / "Finished-100" / "runtimes.csv")
        exp_runtimes["phase"] = exp_runtimes["phase"].str.lower()
        series = exp_runtimes.set_index("phase")["runtime"]
        series["dataset"] = exp.dataset
        series["distance"] = exp.distance
        series["linkage"] = exp.linkage
        series["strategy"] = exp.strategy
        entries.append(series)
    df = pd.DataFrame(entries)
    df = save(df)

    s_max_runtime = df.groupby(["dataset", "distance", "linkage"])["finished"].max()
    df = df.set_index(["dataset", "distance", "linkage", "strategy"]).sort_index()
    for (dataset, distance, linkage, strategy) in tqdm(df.index, desc="Assessing qualities", file=sys.stderr):
        max_runtime = s_max_runtime.loc[(dataset, distance, linkage)]
        total_runtime = df.loc[(dataset, distance, linkage, strategy), "finished"]
        runtimes, whs_r_auc = assess_quality(
            strategy, dataset, distance, linkage, thresholds=thresholds,
            measure="hierarchy-quality", max_runtime=max_runtime,
            total_runtime=total_runtime
        )
        for t in thresholds:
            df.loc[(dataset, distance, linkage, strategy), f"runtime_{t:.1f}"] = runtimes[t]
        df.loc[(dataset, distance, linkage, strategy), "whs_r_auc"] = whs_r_auc
    df = df.reset_index()
    save(df, overwrite=True)
    print("... done.", file=sys.stderr)


if __name__ == "__main__":
    main()
