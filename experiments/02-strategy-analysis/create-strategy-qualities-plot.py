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
colors["shortestTs"] = "gray"
colors["approxAscending"] = "red"
colors["approxDescending"] = "chocolate"
colors["approxFullError"] = "green"
colors["preClustering"] = "purple"

ariQualityMeasures = ("ari", "ariAt", "averageAri", "approxAverageAri")
selected_strategies = (
    "preClustering",
    "approxAscending",
    "approxDescending",
    "approxFullError",
    "shortestTs",
)
histogram_bins = 10


def parse_args(args):
    parser = argparse.ArgumentParser(
        description="Analyze the ordering strategy experiment and plot the results."
    )
    parser.add_argument(
        "resultfolder",
        type=str,
        help="The folder containing the strategy and trace CSV files to analyze.",
    )
    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    results_folder = Path(args.resultfolder)

    parts = results_folder.stem.split("-")
    distance = parts[-3]
    linkage = parts[-2]
    quality_measure = parts[-1].split(".")[0]
    print(f"Creating plot for {distance} {linkage} with {quality_measure} AUC")

    strategy_files = list(results_folder.glob("strategies-*.csv"))
    strategy_files.sort()
    # parse result file name for dataset, n, and seed
    if len(strategy_files) < 1:
        raise ValueError("There are no 'strategies-*.csv'-files in the result folder!")

    fig, axs = plt.subplots(
        1,
        len(strategy_files),
        sharey="all",
        figsize=(int(1.3 * len(strategy_files)), 3),
        squeeze=False,
    )
    axs = axs.flatten()
    for i, results_file in enumerate(strategy_files):
        filename = results_file.stem
        plot_results(results_folder, filename, quality_measure, ax=axs[i])

    handles, labels = axs[0].get_legend_handles_labels()
    fig.legend(
        handles,
        labels,
        loc="lower center",
        ncol=len(labels),
        bbox_to_anchor=(0.5, -0.11),
        borderpad=0.5,
        handletextpad=0.25,
        columnspacing=0.75,
    )

    figures_dir = results_folder.parent.parent
    fig.savefig(
        figures_dir / f"strategy-qualities-{distance}-{linkage}.pdf",
        bbox_inches="tight",
    )
    plt.tight_layout()
    plt.show()


def plot_results(result_dir, filename, quality_measure="ari", ax=None):
    parts = filename.split("-")
    suffix = "-".join(parts[1:])
    tracesPath = result_dir / f"traces-{suffix}.csv"
    strategiesPath = result_dir / f"strategies-{suffix}.csv"
    if len(parts) > 2:
        dataset = parts[-2]
        seed = int(parts[-1])
    else:
        dataset = parts[-1]
        seed = None

    print()
    print(f"Processing dataset '{dataset}' with seed {seed}")

    if not tracesPath.exists():
        raise FileNotFoundError(f"Traces file {tracesPath} not found!")

    if ax is None:
        ax = plt.gca()

    # load and preprocess data
    df = pd.read_csv(tracesPath, header=None)
    df_strategies = pd.read_csv(strategiesPath)

    aucs = df.sum(axis=1) / df.shape[1]
    if "auc" not in df_strategies.columns:
        df_strategies["auc"] = df_strategies["index"].apply(lambda i: aucs.loc[i])

    for _, row in df_strategies.iterrows():
        strategy = row["strategy"]
        auc = row["auc"]
        print(f"  {strategy} achieved AUC={auc:.2f}")

    # select random strategies and fit distribution
    random_strategy_indices = [i for i in df.index if i not in df_strategies["index"]]
    random_aucs = aucs.loc[random_strategy_indices]
    try:
        a, loc, scale = skewnorm.fit(random_aucs.values, loc=0.5, method="MLE")
        print(
            "  skewnorm distribution for random orderings: "
            f"{a=:.2f}, {loc=:.2f}, {scale=:.2f}"
        )
    except Exception as e:
        print(f"  error fitting skewnorm distribution: {e}")
        a, loc, scale = 0, 0.5, 0.5
    dist = skewnorm(a, loc, scale)

    # construct plot
    # - histogram of random strategies
    random_aucs.plot(
        kind="hist",
        density=1,
        bins=histogram_bins,
        stacked=False,
        color="lightgray",
        orientation="horizontal",
        label="Random strategies",
        ax=ax,
    )
    # - fitted distribution for random strategies
    y = np.linspace(-0.5, 1.0, 1000)
    ax.plot(
        dist.pdf(y),
        y,
        linestyle="-",
        lw=2,
        color="gray",
        label="Skewed normal distribution\nfor random strategies",
    )
    # - AUCs of selected strategies
    for strategy in selected_strategies:
        row = df_strategies.loc[df_strategies["strategy"] == strategy]
        if row.empty:
            continue
        strategy = row["strategy"].item()
        auc = row["auc"].item()
        color = colors[strategy]
        ax.axhline(y=auc, linestyle="--", lw=2, color=color, label=strategy)
    # - configure axis
    ax.set_title(dataset)
    if quality_measure in ariQualityMeasures:
        ax.set_ylim(-0.55, 1.05)
    else:
        ax.set_ylim(-0.05, 1.05)
    ax.set_ylabel(f"{quality_measure} AUC")
    ax.axes.get_xaxis().set_visible(False)


if __name__ == "__main__":
    main(sys.argv[1:])
