#!/usr/bin/env python3
import argparse
import sys

from matplotlib.patches import Patch
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from scipy.stats import skewnorm
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parent.parent))
from plt_commons import colors, markers, strategy_name, dataset_name


ariQualityMeasures = ("ari", "ariAt", "averageAri", "approxAverageAri")
selected_strategies = (
    # "approxFullError",
    "approxAscending",
    "preClustering",
    "fcfs",
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
    parser.add_argument(
        "--boxplot",
        action="store_true",
        help="Plot the AUCs of the random strategies as boxplots instead of histogram.",
    )
    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    results_folder = Path(args.resultfolder)
    fit_distribution = args.fit_distribution
    legend_right = args.legend_right
    boxplot = args.boxplot

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

    if boxplot:
        fig, ax = plt.subplots(figsize=(8, 1.5), constrained_layout=True)
        violin_handle, violin_label = plot_results_boxplot(results_folder, strategy_files, quality_measure, ax=ax)
        handles, labels = ax.get_legend_handles_labels()
        handles.append(violin_handle)
        labels.append(violin_label)
    else:
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
            plot_results_histogram(results_folder, filename, quality_measure, fit_distribution, ax=axs[i, j])

        handles, labels = axs[0, 0].get_legend_handles_labels()

    if boxplot:
        legend_title = None
    elif quality_measure == "weightedHierarchySimilarity":
        legend_title = "WHS-S-AUC"
    else:
        legend_title = f"{quality_measure} AUC"
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
    elif boxplot:
        # top
        legend = fig.legend(
            handles,
            labels,
            loc="lower center",
            ncol=len(labels),
            bbox_to_anchor=(0.5, 1.0),
            borderpad=0.25,
            handletextpad=0.4,
            columnspacing=0.75,
        )
    else:
        # bottom
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


def plot_results_histogram(result_dir, filename, quality_measure="ari", fit_distribution=False, ax=None):
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


def plot_results_boxplot(result_dir, strategy_files, quality_measure="ari", ax=None):
    if ax is None:
        ax = plt.gca()

    # load and preprocess data
    aucs = {}
    random_aucs = {}
    for file in strategy_files:
        filename = file.stem
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

        df = pd.read_csv(tracesPath, header=None)
        df_strategies = pd.read_csv(strategiesPath)

        tmp_aucs = df.sum(axis=1) / df.shape[1]
        if "auc" not in df_strategies.columns:
            df_strategies["auc"] = df_strategies["index"].apply(lambda i: tmp_aucs.loc[i])

        aucs[dataset] = df_strategies.set_index("strategy")["auc"]

        # select random strategies
        random_strategy_indices = [i for i in df.index if i not in df_strategies["index"]]
        random_aucs[dataset] = tmp_aucs.loc[random_strategy_indices].values

    datasets = sorted(aucs.keys())
    x = np.arange(len(datasets))

    # add 0.5 line
    ax.axhline(y=0.5, linestyle="--", lw=1, color="lightgray", zorder=-10)

    # construct plot
    # - violinplot of random strategies
    violin_parts = ax.violinplot(
        [random_aucs[dataset] for dataset in datasets],
        positions=x,
        widths=0.8,
        # showmeans=True,
        # showextrema=True,
    )
    for part in violin_parts:
        if part != "bodies":
            violin_parts[part].set_facecolor("gray")
            violin_parts[part].set_edgecolor("gray")
            violin_parts[part].set_linewidth(1)
    for pc in violin_parts["bodies"]:
        pc.set_facecolor("gray")
        pc.set_edgecolor("gray")
        pc.set_linewidth(1)

    # - AUCs of selected strategies
    for i, strategy in enumerate(selected_strategies):
        data = [aucs[dataset].loc[strategy] for dataset in datasets]
        color = colors[strategy]
        marker = markers[strategy]
        ax.scatter(
            x,
            data,
            marker=marker,
            color=color,
            label=strategy_name(strategy),
            linewidth=0,
            # increase size of marker
            s=100,
            zorder=2.5 + i*0.01,
        )
    # - configure axis
    # if quality_measure in ariQualityMeasures:
    #     ax.set_ylim(-0.55, 1.05)
    # else:
    #     ax.set_ylim(-0.05, 1.05)

    ax.set_xticks(x)
    ax.set_xticklabels([dataset_name(dataset) for dataset in datasets], rotation=25, ha="right")
    ax.set_yticks([0.25, 0.5, 0.75])

    if quality_measure == "weightedHierarchySimilarity":
        ax.set_ylabel("WHS-S-AUC")
    else:
        ax.set_ylabel(f"{quality_measure} AUC")

    # spines = ax.spines
    # spines["top"].set_visible(False)
    # spines["right"].set_visible(False)
    # spines["left"].set_visible(False)
    return Patch(color="lightgray"), "Random strategies"


if __name__ == "__main__":
    main(sys.argv[1:])
