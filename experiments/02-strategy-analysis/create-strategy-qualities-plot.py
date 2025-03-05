#!/usr/bin/env python3
import argparse
import sys

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from scipy.stats import skewnorm
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parent.parent))
from plt_commons import colors, strategy_name, dataset_name


ariQualityMeasures = ("ari", "ariAt", "averageAri", "approxAverageAri")
selected_strategies = (
    "preClustering",
    "approxAscending",
    # "approxFullError",
    "shortestTs",
    "fcfs",
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
    parser.add_argument(
        "--fit-distribution",
        action="store_true",
        help="Fit a skewed normal distribution to the AUCs of random strategies.",
    )
    parser.add_argument(
        "--legend-right",
        action="store_true",
        help="Place the legend to the right of the plot, instead on the bottom.",
    )
    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    results_folder = Path(args.resultfolder)
    fit_distribution = args.fit_distribution
    legend_right = args.legend_right

    parts = results_folder.stem.split("-")
    distance = parts[-3]
    linkage = parts[-2]
    quality_measure = parts[-1].split(".")[0]
    print(f"Creating plot for {distance} {linkage} with {quality_measure} AUC")

    strategy_files = list(results_folder.glob("strategies-*.csv"))
    strategy_files.sort()
    n = len(strategy_files)
    if n < 1:
        raise ValueError("There are no 'strategies-*.csv'-files in the result folder!")

    fig, axs = plt.subplots(
        2,
        int(np.ceil(n / 2)),
        sharex="all",
        figsize=(8, 1.5),
        squeeze=False,
        constrained_layout=True,
    )
    for k, results_file in enumerate(strategy_files):
        filename = results_file.stem
        i = int(k / (n/2))
        j = int(k % (n/2))
        plot_results(results_folder, filename, quality_measure, fit_distribution, ax=axs[i, j])

    if quality_measure == "weightedHierarchySimilarity":
        legend_title = "WHS-S-AUC"
    else:
        legend_title = f"{quality_measure} AUC"

    handles, labels = axs[0, 0].get_legend_handles_labels()
    if legend_right:
        legend = fig.legend(
            handles,
            labels,
            title=legend_title,
            loc="center left",
            ncol=1,
            bbox_to_anchor=(1, 0.5),
            borderpad=0.25,
            handletextpad=0.4,
        )
    else:
        legend = fig.legend(
            handles,
            labels,
            loc="upper center",
            ncol=len(labels),
            bbox_to_anchor=(0.5, 0.0),
            borderpad=0.25,
            handletextpad=0.4,
            columnspacing=0.75,
            title=legend_title,
        )

    figures_dir = results_folder.parent.parent
    fig.savefig(
        figures_dir / f"strategy-qualities-{distance}-{linkage}.pdf",
        bbox_inches="tight",
        bbox_extra_artists=(legend,),
    )
    # plt.tight_layout()
    # plt.show()


def plot_results(result_dir, filename, quality_measure="ari", fit_distribution=False, ax=None):
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
    if fit_distribution:
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
        orientation="vertical",
        label="Random strategies",
        ax=ax,
    )
    # - fitted distribution for random strategies
    if fit_distribution:
        x = np.linspace(0, 1.0, 1000)
        ax.plot(
            x,
            dist.pdf(x),
            linestyle="-",
            lw=2,
            color="gray",
            label="Skewed normal\ndistribution (random)",
        )
    # - AUCs of selected strategies
    for strategy in selected_strategies:
        row = df_strategies.loc[df_strategies["strategy"] == strategy]
        if row.empty:
            continue
        strategy = row["strategy"].item()
        auc = row["auc"].item()
        color = colors[strategy]
        ax.axvline(
            x=auc, linestyle="--", lw=2, color=color, label=strategy_name(strategy)
        )
    # - configure axis
    ax.set_title(dataset_name(dataset), fontsize=10)
    if quality_measure in ariQualityMeasures:
        ax.set_xlim(-0.55, 1.05)
    else:
        ax.set_xlim(-0.05, 1.05)
    # if quality_measure == "weightedHierarchySimilarity":
    #     ax.set_xlabel("WHS-S-AUC")
    # else:
    #     ax.set_xlabel(f"{quality_measure} AUC")
    ax.axes.get_yaxis().set_visible(False)
    spines = ax.spines
    spines["top"].set_visible(False)
    spines["right"].set_visible(False)
    spines["left"].set_visible(False)


if __name__ == "__main__":
    main(sys.argv[1:])
