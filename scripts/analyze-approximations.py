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
    parser.add_argument("resultfolder", type=str,
                        help="The folder where the approximation CSV files are stored.")
    parser.add_argument("--n", type=int, default=20,
                        help="The snippet length for which the approximations were computed.")

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    results_folder = Path(args.resultfolder)
    n = args.n
    plot_results(results_folder, n)


def plot_results(result_dir, n):
    quality_measure = result_dir.stem.split("-")[-1].split(".")[0]
    if quality_measure not in ["ari", "hierarchy"]:
        raise ValueError(f"Unknown quality measure '{quality_measure}' in result directory name '{result_dir.stem}'")

    figures_dir = result_dir / "figures"
    figures_dir.mkdir(parents=True, exist_ok=True)
    dfs = []
    for file in result_dir.glob(f"approx-strategies-*-{n}.csv"):
        # parse result file name for dataset, n, and seed
        dataset = file.stem.split("-")[2]
        print(f"Loading dataset '{dataset}' with snippet length {n}")
        df = pd.read_csv(file)
        dfs.append(df)

    df = pd.concat(dfs)
    n_metrics = df["metric"].unique().size
    n_linkages = df["linkage"].unique().size
    n_datasets = df["dataset"].unique().size

    # aggregate over everything
    df_all = df.groupby(["snippetSize", "approx-strategy"])[["quality"]]\
               .agg(["mean", "std"])\
               .sort_values(("quality", "mean"), ascending=False)
    df_all = df_all.reset_index()
    df_all.columns = ["snippetSize", "approx-strategy", "quality-mean", "quality-std"]
    print(f"Best strategies for {n_datasets} datasets with snippet length {n}, {n_metrics} metrics, and {n_linkages} linkages:")
    print(df_all)

    plt.figure()
    plt.title(f"Quality of Approximations for Snippet Length {n}")
    _bar_plot(df_all)
    if quality_measure == "ari":
        plt.ylabel("Quality [-0.5, 1.0]")
    else:
        plt.ylabel("Hierarchy Similarity [0.0, 1.0]")
    plt.legend()
    plt.tight_layout()
    plt.savefig(figures_dir / f"all-{n}.pdf", bbox_inches='tight')

    # aggregate over datasets and linkages
    df_distances = df.groupby(["snippetSize", "metric", "approx-strategy"])[["quality"]]\
                     .agg(["mean", "std"])\
                     .sort_values(("quality", "mean"), ascending=False)
    df_distances = df_distances.reset_index()
    df_distances.columns = ["snippetSize", "distance", "approx-strategy", "quality-mean", "quality-std"]
    all_distances = df_distances["distance"].unique()
    fig, axs = plt.subplots(1, len(all_distances), figsize=(5 * len(all_distances), 5), sharey="all")
    for i, distance in enumerate(all_distances):
        df_tmp = df_distances[df_distances["distance"] == distance]
        print(f"Best strategies for distance {distance}:")
        print(df_tmp)

        axs[i].set_title(f"Distance {distance}")
        _bar_plot(df_tmp, ax=axs[i])

    if quality_measure == "ari":
        axs[0].set_ylabel("Quality [-0.5, 1.0]")
    else:
        axs[0].set_ylabel("Hierarchy Similarity [0.0, 1.0]")
    legend_handles, legend_labels = axs[0].get_legend_handles_labels()
    fig.legend(legend_handles, legend_labels, loc="upper center", ncol=len(legend_labels), bbox_to_anchor=(0.5, 1.05))
    plt.tight_layout()
    plt.savefig(figures_dir / f"distances-{n}.pdf", bbox_inches='tight')

    # aggregate over datasets and distances
    df_linkages = df.groupby(["snippetSize", "linkage", "approx-strategy"])[["quality"]]\
                     .agg(["mean", "std"])\
                     .sort_values(("quality", "mean"), ascending=False)
    df_linkages = df_linkages.reset_index()
    df_linkages.columns = ["snippetSize", "linkage", "approx-strategy", "quality-mean", "quality-std"]
    all_linkages = df_linkages["linkage"].unique()
    fig, axs = plt.subplots(1, len(all_linkages), figsize=(5 * len(all_linkages), 5), sharey="all")
    for i, linkage in enumerate(all_linkages):
        df_tmp = df_linkages[df_linkages["linkage"] == linkage]
        print(f"Best strategies for linkage {linkage}:")
        print(df_tmp)

        axs[i].set_title(f"Linkage {linkage}")
        _bar_plot(df_tmp, ax=axs[i])

    if quality_measure == "ari":
        axs[0].set_ylabel("Quality [-0.5, 1.0]")
    else:
        axs[0].set_ylabel("Hierarchy Similarity [0.0, 1.0]")
    legend_handles, legend_labels = axs[0].get_legend_handles_labels()
    fig.legend(legend_handles, legend_labels, loc="upper center", ncol=len(legend_labels), bbox_to_anchor=(0.5, 1.05))
    plt.tight_layout()
    plt.savefig(figures_dir / f"linkages-{n}.pdf", bbox_inches='tight')
    plt.show()


def _bar_plot(df, ax=None, quality_measure="ari"):
    if ax is None:
        ax = plt.gca()
    for i, (_, row) in enumerate(df.iterrows()):
        strategy = row["approx-strategy"]
        c = colors[strategy]
        ax.bar(strategy, row["quality-mean"], yerr=row["quality-std"], color=c, label=strategy, align="center")
        ax.text(i, - 0.1, f"{row['quality-mean']:.2f}", ha="center", va="center", color="black")

    ax.set_xlabel("Approx. Strategy")
    ax.tick_params(axis="x", labelrotation=45)
    ax.set_ylim(-0.2, 0.5)

if __name__ == '__main__':
    main(sys.argv[1:])
