#!/usr/bin/env python
import sys
import argparse

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from pathlib import Path
from scipy.stats import kurtosis, skew

from aeon.datasets import load_classification
from aeon.distances import pairwise_distance, distance

cmap = plt.get_cmap("tab10")


def parse_args(args):
    parser = argparse.ArgumentParser(description="Load a TS ordering and compute statistics from the distances and "
                                     "the dataset to correlate with the ordering.")
    parser.add_argument("resultfile", type=str,
                        help="The TS ordering CSV file to analyze.")

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    resultfile = Path(args.resultfile)
    result_folder = resultfile.parent
    dataset = resultfile.stem.split("-")[-1]
    compute_stats(dataset, result_folder, resultfile)


def compute_stats(dataset, result_folder, resultfile):
    print(f"Loading TS ordering from {resultfile}")
    df = pd.read_csv(resultfile, index_col=0, header=None)
    df.columns = ["mean_pos"]
    df.index.name = "ID"
    print(df.head(10))

    print()
    print(f"Loading dataset {dataset} and computing pairwise distances")
    X, y = load_classification(dataset, extract_path="data/datasets/", load_equal_length=False)
    ymap = {label: i for i, label in enumerate(np.unique(y))}
    dists = pd.DataFrame(pairwise_distance(X, metric="msm"))
    X = [x.ravel() for x in X]
    dists_approx = pd.DataFrame(_pairwise_approx_distance(X, metric="msm"))
    print("Mean exact distances:", dists.mean().mean())
    print("Mean approx distances:", dists_approx.mean().mean())

    print()
    print("Computing statistics")
    df = df.sort_index()
    df["ts_mean"] = [np.mean(x) for x in X]
    df["ts_std"] = [np.std(x) for x in X]
    df["ts_skew"] = [skew(x) for x in X]
    df["ts_kurtosis"] = [kurtosis(x) for x in X]
    df["exact_dist_min"] = dists.replace(0, np.nan).min(axis=1)
    df["exact_dist_mean"] = dists.mean(axis=1)
    df["exact_dist_std"] = dists.std(axis=1)
    df["exact_dist_max"] = dists.max(axis=1)
    df["exact_skew"] = dists.apply(skew, axis=1)
    df["exact_kurtosis"] = dists.apply(kurtosis, axis=1)
    df["approx_dist_min"] = dists_approx.replace(0, np.nan).min(axis=1)
    df["approx_dist_mean"] = dists_approx.mean(axis=1)
    df["approx_dist_std"] = dists_approx.std(axis=1)
    df["approx_dist_max"] = dists_approx.max(axis=1)
    df["approx_skew"] = dists_approx.apply(skew, axis=1)
    df["approx_kurtosis"] = dists_approx.apply(kurtosis, axis=1)
    print("Correlation with mean position (kendall):")
    s_corr = df.corr("kendall")["mean_pos"]
    print(s_corr)
    # store to CSV
    df.to_csv(result_folder / f"ts-distance-statistics-{dataset}.csv")

    print()
    print("Plotting top and bottom 10 TS")
    top10 = df.sort_values("mean_pos").head(10).index
    bottom10 = df.sort_values("mean_pos").tail(10).index
    fig, axes = plt.subplots(2, 1, figsize=(10, 10))
    for i, ts_id in enumerate(top10):
        color = cmap.colors[ymap[y[ts_id]]]
        axes[0].plot(X[ts_id].ravel(), color=color, label=f"TS {ts_id}")
    axes[0].set_title("Top 10 TS")
    axes[0].legend()
    for i, ts_id in enumerate(bottom10):
        color = cmap.colors[ymap[y[ts_id]]]
        axes[1].plot(X[ts_id].ravel(), color=color, label=f"TS {ts_id}")
    axes[1].set_title("Bottom 10 TS")
    axes[1].legend()
    plt.show()


def _pairwise_approx_distance(X, metric="msm", snippet_size=20):
    n = len(X)
    dists = np.zeros((n, n))
    for i in range(n):
        for j in range(i + 1, n):
            ts1 = X[i]
            ts2 = X[j]
            factor = max(len(ts1), len(ts2)) / snippet_size
            dists[i, j] = distance(slice_center(ts1, snippet_size), slice_center(ts2, snippet_size), metric=metric) * factor
            dists[j, i] = dists[i, j]
    return dists


def slice_center(x, length):
    center = len(x) // 2
    return x[center - length//2 : center + length//2]



if __name__ == "__main__":
    main(sys.argv[1:])
