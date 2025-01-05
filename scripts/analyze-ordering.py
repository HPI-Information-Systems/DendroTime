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
colors["fcfs"] = "dimgray"
colors["shortestTs"] = "gray"
colors["approxAscending"] = "chocolate"
colors["approxDescending"] = "saddlebrown"
colors["highestVar"] = "darkgray"
colors["gtLargestPairError"] = "brown"
colors["gtLargestTsError"] = "darkgray"
colors["approxDiffTsError"] = "orange"
# these are the same strategies:
colors["dynamicError"] = "lightgray"
colors["approxFullError"] = "lightgray"
# strategies of interest
colors["preClustering"] = "red"
colors["recursivePreClustering"] = "green"
colors["advancedPreClustering"] = "purple"
# colors for different progress indicators
colors["preClustering-log"] = "chocolate"
colors["preClustering-weighted"] = "green"
colors["preClustering-ind-averageARI"] = "red"
colors["preClustering-ind-changes@k"] = "orange"


def parse_args(args):
    parser = argparse.ArgumentParser(description="Analyze an ordering strategy experiment and plot the results.")
    parser.add_argument("resultfile", type=str,
                        help="The strategy CSV file to analyze.")
    parser.add_argument("-b", "--save-best-ts-ordering", action="store_true",
                        help="Take best ordering, sort TS IDs based on their position in the ordering, and save to a CSV file.")
    parser.add_argument("-i", "--ignore-debug", action="store_true",
                        help="Ignore debug information even if present.")

    return parser.parse_args(args)


def parse_order(order):
    values = order.split(" ")
    tuples = list(eval(v) for v in values)
    return tuples


def main(sys_args):
    args = parse_args(sys_args)
    results_file = Path(args.resultfile)
    save_best = args.save_best_ts_ordering
    ignore_debug = args.ignore_debug
    # parse result file name for dataset, n, and seed
    filename = results_file.stem
    if not filename.startswith("strategies"):
        raise ValueError("The filename must start with 'strategies'")

#     result_dir = Path.cwd() / "experiments" / "ordering-strategy-analysis"
    result_dir = results_file.parent
    quality_measure = result_dir.stem.split("-")[-1].split(".")[0]
#     if quality_measure not in ["ari", "target_ari", "averageari", "hierarchy", "weighted"]:
#         raise ValueError(f"Unknown quality measure '{quality_measure}' in result directory name '{result_dir.stem}'")
    plot_results(result_dir, filename, quality_measure, save_best, ignore_debug)


def plot_results(result_dir, filename, quality_measure="ari", save_best=False, ignore_debug=False):
    suffix = "-".join(filename.split("-")[1:])
    if len(filename.split("-")) == 4:
        n = int(filename.split("-")[1])
        dataset = filename.split("-")[2]
        seed = int(filename.split("-")[3])
    else:
        dataset = filename.split("-")[-1]
        n = int(filename.split("-")[-2])
        seed = None

    print(f"Processing dataset '{dataset}' with {n} time series and seed {seed}")
    if seed is None:
        tracesPath = result_dir / f"traces-{suffix}.csv"
        orderingsPath = None
        strategiesPath = result_dir / f"strategies-{suffix}.csv"
    else:
        tracesPath = result_dir / f"traces-{suffix}.csv"
        orderingsPath = result_dir / f"orderings-{n}.csv"
        strategiesPath = result_dir / f"strategies-{suffix}.csv"
    preClusterDebugPath = result_dir / f"preCluster-debug-{suffix}.csv"

    if not tracesPath.exists():
        raise FileNotFoundError(f"Traces file {tracesPath} not found!")

    figures_dir = result_dir / "figures"
    figures_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(tracesPath, header=None)
    df_strategies = pd.read_csv(strategiesPath)
    m = n*(n-1)//2

    aucs = df.sum(axis=1)/df.shape[1]
    if "auc" not in df_strategies.columns:
        df_strategies["auc"] = df_strategies["index"].apply(lambda i: aucs.loc[i])
    aucs = aucs.sort_values()

    try:
        a, loc, scale = skewnorm.fit(aucs.values, loc=0.5, method="MLE")
        print()
    except Exception as e:
        print(f"Error fitting skewnorm distribution: {e}")
        a, loc, scale = 0, 0.5, 0.5
    print(f"Skewnorm distribution: {a=}, {loc=}, {scale=}")
    dist = skewnorm(a, loc, scale)

    print()
    print(f"Best ordering ({aucs.iloc[-1].item():.2f})")
    best_order = parse_order(df_strategies.iloc[-1]["order"])
    print(best_order[:20], "...")

    if save_best:
        best_order_folder = result_dir.parent / f"best-ts-order-{quality_measure}"
        best_order_folder.mkdir(parents=True, exist_ok=True)
        print()
        print(f"Extracting best ordering and saving to CSV file in {best_order_folder}")
        mean_pos = defaultdict(lambda: 0)
        for idx, (i, j) in enumerate(best_order):
            mean_pos[i] += idx
            mean_pos[j] += idx
        mean_pos = pd.DataFrame([mean_pos]).T
        mean_pos.index.name = "Time series ID"
        mean_pos.columns = ["Mean position"]
        mean_pos = mean_pos / n
        mean_pos = mean_pos.sort_values("Mean position", ascending=True)

        print()
        print(mean_pos.head(10))
        mean_pos.to_csv(f"{best_order_folder}/best-ts-order-{dataset}.csv", header=False)

    if orderingsPath is not None:
        df_orderings = pd.read_csv(result_dir / f"orderings-{n}.csv", header=None)
        print(f"Top 5 orderings (~{aucs.iloc[-1].item():.2f})")
        for i in aucs.index[-5:]:
            values = df_orderings.iloc[i].values
            tuples = list(zip(values[::2], values[1::2]))
            print(tuples)

    print()
    for _, row in df_strategies.iterrows():
        strategy = row["strategy"]
        i = row["index"]
        auc = row["auc"]
        print(f"{strategy} ({auc:.2f})")
        if "order" in row:
            tuples = parse_order(row["order"])
        elif orderingsPath is not None:
            values = df_orderings.iloc[i].values
            tuples = list(zip(values[::2], values[1::2]))
        print(tuples[:20])

    plt.figure()
    plt.title("Distribution of ordering quality")
    aucs.plot(kind="hist", density=1, bins=30, stacked=False, alpha=0.5, label="AUC histogram")
    x = np.linspace(-0.5, 1.0, 1500)
    plt.plot(x, dist.pdf(x), "k-", lw=2,
        label=f"Skewed Normal Distribution\n(a={a:.2f}, loc={loc:.2f}, scale={scale:.2f})"
    )
    for _, row in df_strategies.iterrows():
        strategy = row["strategy"]
        auc = row["auc"]
        color = colors[strategy]
        plt.axvline(x=auc, linestyle="--", color=color, label=strategy)
    plt.xlabel("Solution quality")
    plt.ylabel("Frequency")
    if "ari" in quality_measure:
        plt.xlim(-0.55, 1.05)
    else:
        plt.xlim(-0.05, 1.05)
    plt.legend(ncol=2)
    plt.savefig(figures_dir / f"hist-{n}-{dataset}-{seed}.pdf", bbox_inches='tight')

    plt.figure()
    plt.title("Solutions")

    # add debug information if present
    if preClusterDebugPath.exists() and not ignore_debug:
        df_debug = pd.read_csv(preClusterDebugPath)
        for _, row in df_debug.iterrows():
            if row["type"] == "STATE":
                plt.axvline(x=row["index"], linestyle="--", color="black", lw=1)
            elif row["type"] == "INTER":
                plt.axvline(x=row["index"], linestyle="-.", color="gray", lw=0.5)

    best_id = aucs.index[-1]
    worst_id = aucs.index[0]
    factor = int(np.floor(m / min(1000, m)))
    index = np.r_[0, 1+np.arange(0, df.shape[1]-2)*factor, m]
    plt.plot(index, df.iloc[best_id, :], linestyle="--", color="black", label="Best ordering")
    plt.plot(index, df.iloc[worst_id, :], linestyle="--", color="black", label="Worst ordering")
    for _, row in df_strategies.iterrows():
        strategy = row["strategy"]
        i = row["index"]
        color = colors[strategy]
        plt.plot(index, df.iloc[i, :], color=color, label=strategy)

    prototype_quality_path = Path(f"data/results/{dataset}-Finished-100/quality.csv")
    if prototype_quality_path.exists():
        df2 = pd.read_csv(prototype_quality_path)
        df2["index"] = df2["index"] - m
        df2 = df2[df2["index"] >= 0]
        df2 = df2.set_index("index")
        name = "Hierarchy Quality"
        plt.plot(df2.index, df2["hierarchy-quality"], color=colors[name], label=name)

    plt.xlabel(f"Available distances (of {n*(n-1)/2})")
    plt.ylabel(quality_measure)
    if "ari" in quality_measure:
        plt.ylim(-0.55, 1.05)
    else:
        plt.ylim(-0.05, 1.05)

    plt.legend(ncol=2)
    plt.savefig(figures_dir / f"solutions-{n}-{dataset}-{seed}.pdf", bbox_inches='tight')
    plt.show()


if __name__ == '__main__':
    main(sys.argv[1:])
