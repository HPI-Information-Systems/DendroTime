#!/usr/bin/env python3
import argparse
import sys

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy.stats import skewnorm
from collections import defaultdict
from pathlib import Path


colors = defaultdict(lambda: "blue")
colors["fcfs"] = "green"
colors["shortestTs"] = "cyan"
colors["approxAscending"] = "red"
colors["approxDescending"] = "orange"
colors["highestVar"] = "purple"
colors["gtLargestPairError"] = "brown"
colors["gtLargestTsError"] = "darkgray"
colors["approxDiffTsError"] = "olive"
# those names changed in the new version:
colors["dynamicError"] = "pink"
colors["approxFullError"] = "pink"


def parse_args(args):
    parser = argparse.ArgumentParser(description="Combine the results of two ordering strategy experiments.")
    parser.add_argument("target_experiment", type=str,
                        help="The target experiment strategy CSV file to store the combined results in.")
    parser.add_argument("source_experiment_folder", type=str,
                        help="The folder where the experiment results to combine with the target experiment are stored in.")

    return parser.parse_args(args)


def parse_order(order):
    values = order.split(" ")
    tuples = list(eval(v) for v in values)
    return tuples


def main(sys_args):
    args = parse_args(sys_args)
    target_experiment_file = Path(args.target_experiment).resolve()
    source_experiment_folder = Path(args.source_experiment_folder).resolve()

    # parse result file name for dataset, n, and seed
    filename = target_experiment_file.stem
    if not filename.startswith("strategies"):
        raise ValueError("The filename must start with 'strategies'")

#     result_dir = Path.cwd() / "experiments" / "ordering-strategy-analysis"
    result_dir = target_experiment_file.parent
    quality_measure = result_dir.stem.split("-")[-1].split(".")[0]
    if quality_measure not in ["ari", "hierarchy", "weighted"]:
        raise ValueError(f"Unknown quality measure '{quality_measure}' in result directory name '{result_dir.stem}'")

    source_quality_measure = source_experiment_folder.stem.split("-")[-1].split(".")[0]
    if source_quality_measure != quality_measure:
        raise ValueError(f"Quality measure '{source_quality_measure}' in target experiment does not match quality measure in source experiment '{quality_measure}'")

    combine_results(result_dir, filename, source_experiment_folder)


def combine_results(result_dir, filename, source_experiment_folder):
    suffix = "-".join(filename.split("-")[1:])
    dataset = filename.split("-")[-1]
    n = int(filename.split("-")[-2])

    print(f"Combining results for dataset '{dataset}' with {n} time series: from {source_experiment_folder.stem} in {result_dir.stem}")
    targetTracesPath = result_dir / f"traces-{suffix}.csv"
    sourceTracesPath = source_experiment_folder / f"traces-{suffix}.csv"
    targetStrategiesPath = result_dir / f"strategies-{suffix}.csv"
    sourceStrategiesPath = source_experiment_folder / f"strategies-{suffix}.csv"

    if not targetTracesPath.exists():
        raise FileNotFoundError(f"Target traces file {targetTracesPath} not found!")

    if not sourceStrategiesPath.exists():
        sourceStrategiesPath = source_experiment_folder / f"strategies-{n}-{dataset}.csv"
        print(f"Source strategies file 'strategies-{n}-{dataset}.csv' not found, trying {sourceStrategiesPath.stem} instead.")
        if not sourceStrategiesPath.exists():
            raise FileNotFoundError(f"No corresponding source strategies file {sourceStrategiesPath} found!")

    if not sourceTracesPath.exists():
        sourceTracesPath = source_experiment_folder / f"traces-{n}-{dataset}.csv"
        print(f"Source traces file 'traces-{n}-{dataset}.csv' not found, trying {sourceTracesPath.stem} instead.")
        if not sourceTracesPath.exists():
            raise FileNotFoundError(f"Source traces file {sourceTracesPath} not found!")

    df_target_traces = pd.read_csv(targetTracesPath, header=None)
    df_source_traces = pd.read_csv(sourceTracesPath, header=None)
    df_target_strategies = pd.read_csv(targetStrategiesPath)
    df_source_strategies = pd.read_csv(sourceStrategiesPath)

    offset = df_target_traces.shape[0]
    print(f"Target has {offset} strategies, offsetting source indices by {offset}.")
    df_source_traces.index += offset
    df_source_strategies["index"] += offset

    df_traces = pd.concat([df_target_traces, df_source_traces], ignore_index=False)
    df_strategies = pd.concat([df_target_strategies, df_source_strategies], ignore_index=True)
    print(df_strategies[["strategy", "index"]])

    df_traces.to_csv(targetTracesPath, header=False, index=False)
    df_strategies.to_csv(targetStrategiesPath, index=False)


if __name__ == '__main__':
    main(sys.argv[1:])
