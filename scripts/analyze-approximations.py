#!/usr/bin/env python3
import argparse
import sys

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy.stats import skewnorm
from collections import defaultdict
from pathlib import Path


colors = defaultdict(lambda: "black")
colors["begin"] = "green"
colors["end"] = "blue"
colors["center"] = "darkgray"
colors["offsetBegin10"] = "lawngreen"
colors["offsetBegin20"] = "olive"
colors["offsetEnd10"] = "cyan"
colors["offsetEnd20"] = "purple"
colors["twoMean10"] = "orange"
colors["twoMean20"] = "red"

def parse_args(args):
    parser = argparse.ArgumentParser(description="Analyze different approximation strategies and plot the results.")
    parser.add_argument("resultfile", type=str,
                        help="The approximation CSV file to analyze.")

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    results_file = Path(args.resultfile)
    # parse result file name for dataset, n, and seed
    filename = results_file.stem
    if not filename.startswith("approx-strategies"):
        raise ValueError("The filename must start with 'approx-strategies'")

    dataset = filename.split("-")[2]
    n = int(filename.split("-")[3])

#     result_dir = Path.cwd() / "experiments" / "ordering-strategy-analysis"
    result_dir = results_file.parent
    plot_results(result_dir, results_file, dataset, n)


def plot_results(result_dir, file, dataset, n):
    print(f"Processing dataset '{dataset}' with snippet length {n}")
    figures_dir = result_dir / "figures"
    figures_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(file)
    n_metrics = df["metric"].unique().size
    n_linkages = df["linkage"].unique().size
    df = df.groupby(["snippetSize", "approx-strategy"])[["quality"]]\
           .agg(["mean", "std"])\
           .sort_values(("quality", "mean"), ascending=False)
    df = df.reset_index()
    df.columns = ["snippetSize", "approx-strategy", "quality-mean", "quality-std"]
    print(f"Best strategies for dataset '{dataset}' with snippet length {n}, {n_metrics} metrics, and {n_linkages} linkages")
    print(df)

    plt.figure()
    plt.title(f"Quality of approximations for dataset '{dataset}' with snippet length {n}")
    for i, row in df.iterrows():
        strategy = row["approx-strategy"]
        c = colors[strategy]
        plt.bar(strategy, row["quality-mean"], yerr=row["quality-std"], color=c, label=strategy, align="center")
        plt.text(i, - 0.1, f"{row['quality-mean']:+.2f}", ha="center", va="center", color="black")

    plt.xlabel("Approx. Strategy")
    plt.xticks(rotation=45, ha="right")
    plt.ylabel("Mean Quality")
    plt.ylim(-0.2, 0.5)
    plt.legend()
    plt.tight_layout()
    plt.savefig(figures_dir / f"{dataset}-{n}.pdf", bbox_inches='tight')
    plt.show()


if __name__ == '__main__':
    main(sys.argv[1:])
