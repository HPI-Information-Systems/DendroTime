#!/usr/bin/env python3
import argparse
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd

sys.path.append(str(Path(__file__).parent.parent))
from plt_commons import (colors, distances, extract_measures_from_config,
                         linkages, measure_name_mapping, strategy_name)


def parse_args(args):
    parser = argparse.ArgumentParser(
        description="Analyze a DendroTime experiment and plot the qualities."
        "Either provide the result file or the dataset, distance, linkage, and strategy names."
    )
    parser.add_argument(
        "--resultfile", type=str, help="The qualities CSV file to analyze."
    )
    parser.add_argument("--dataset", type=str, help="Dataset name")
    parser.add_argument(
        "--distance",
        type=str,
        default="msm",
        choices=distances,
        help="Distance measure",
    )
    parser.add_argument(
        "--linkage",
        type=str,
        default="average",
        choices=linkages,
        help="Linkage method",
    )
    parser.add_argument(
        "--strategy-left",
        type=str,
        default="approx-distance-ascending",
        choices=["pre-clustering", "approx-distance-ascending", "fcfs"],
        help="Strategy name for left plot",
    )
    parser.add_argument(
        "--strategy-right",
        type=str,
        default="fcfs",
        choices=["pre-clustering", "approx-distance-ascending", "fcfs"],
        help="Strategy name for right plot",
    )
    parser.add_argument(
        "--use-runtime",
        action="store_true",
        help="Use runtime instead of computational steps as x-axis.",
    )
    parser.add_argument(
        "--include-ari",
        action="store_true",
        help="Include ARI in the plot.",
    )
    parser.add_argument(
        "-n",
        "--display-strategy-name",
        action="store_true",
        help="Display the strategy name as titles in the plot.",
    )

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    use_runtime = args.use_runtime
    include_ari = args.include_ari
    display_strategy_name = args.display_strategy_name

    if args.resultfile is not None:
        results_file_left = Path(args.resultfile)
        results_file_right = None
    elif args.dataset is None:
        raise ValueError("Either provide the result file or the dataset name.")
    else:
        dataset = args.dataset
        distance = args.distance
        linkage = args.linkage
        strategy_left = args.strategy_left
        results_file_left = Path(
            f"results/{dataset}-{distance}-{linkage}-{strategy_left.replace('-', '_')}/Finished-100/qualities.csv"
        )
        strategy_right = args.strategy_right
        results_file_right = Path(
            f"results/{dataset}-{distance}-{linkage}-{strategy_right.replace('-', '_')}/Finished-100/qualities.csv"
        )

    results_file_left = results_file_left.resolve()
    if not results_file_left.exists():
        raise FileNotFoundError(f"Result file {results_file_left} not found!")
    if results_file_right is not None:
        results_file_right = results_file_right.resolve()
        if not results_file_right.exists():
            raise FileNotFoundError(f"Result file {results_file_right} not found!")

    fig, axs = plt.subplots(1, 2, sharey="all", figsize=(5.5, 1.5), constrained_layout=True)

    # plot left
    plot_results(results_file_left, use_runtime, include_ari, ax=axs[0])
    if display_strategy_name:
        axs[0].set_title(strategy_name(strategy_left))

    # plot right
    if results_file_right is not None:
        plot_results(results_file_right, use_runtime, include_ari, ax=axs[1])
        if display_strategy_name:
            axs[1].set_title(strategy_name(strategy_right))

    axs[0].set_ylabel("Quality")
    axs[0].set_ylim(0.0, 1.05)

    handles, labels = axs[0].get_legend_handles_labels()
    fig.legend(
        handles, labels,
        loc="lower center",
        ncol=len(handles),
        bbox_to_anchor=(0.5, 1.0),
    )

    fig.savefig(
        f"solutions-{dataset}-{distance}-{linkage}-{strategy_left}-{strategy_right}.pdf",
        bbox_inches="tight",
    )
    # plt.show()


def plot_results(results_file, use_runtime=False, include_ari=False, ax=None):
    if ax is None:
        ax = plt.gca()

    # experiment details
    parts = results_file.parent.parent.stem.split("-")
    dataset = parts[0]
    distance = parts[1]
    linkage = parts[2]
    strategy = parts[3]

    # measure details
    config_file = results_file.parent / "config.json"
    measures = extract_measures_from_config(config_file)

    print(
        f"Processing dataset '{dataset}' with distance '{distance}', linkage '{linkage}', and strategy '{strategy}'"
    )

    df = pd.read_csv(results_file)
    # use relative runtime instead of millis since epoch
    df["timestamp"] = df["timestamp"] - df.loc[0, "timestamp"]
    runtime_unit = "ms"
    if df["timestamp"].max() > 2000:
        # convert millis to seconds
        df["timestamp"] = df["timestamp"] / 1000
        runtime_unit = "s"

    df["index"] = df["index"].astype(int)
    n = df.shape[0]
    middle = df[df["index"] >= n // 2].index[0]
    print("Phase change", middle)
    if use_runtime:
        df = df.set_index("timestamp").drop(columns=["index"])
    else:
        df = df.set_index("index").drop(columns=["timestamp"])

    if not include_ari and "cluster-quality" in df.columns:
        df = df.drop(columns=["cluster-quality"])

    aucs = df.sum(axis=0) / df.shape[0]
    print("AUCs")
    print(aucs)

    # ax.set_title(f"{dataset}: {strategy_name(strategy)} strategy with {distance.upper()} and {linkage}")
    ax.grid(True, which="major", axis="y", ls=":", lw=1)
    ax.axvline(
        x=df.index[middle],
        color="gray",
        linestyle="--",
        label="Approx. $\\rightarrow$ Exact",
    )

    # WHS
    if "hierarchy-quality" in df.columns:
        ax.step(
            df.index,
            df["hierarchy-quality"],
            where="post",
            label=measure_name_mapping[measures["hierarchy-quality"]],
            color=colors["hierarchy-quality"],
            lw=2,
        )
        ax.fill_between(
            df.index,
            df["hierarchy-quality"],
            alpha=0.2,
            step="post",
            color=colors["hierarchy-quality"],
        )
        df = df.drop(columns=["hierarchy-quality"])

    for measurement in df.columns:
        ax.plot(
            df.index,
            df[measurement],
            label=measure_name_mapping[measures[measurement]],
            lw=2,
            color=colors[measurement],
        )

    if use_runtime:
        ax.set_xlabel(f"Runtime ({runtime_unit})")
    else:
        ax.set_xlabel("Computational steps")


if __name__ == "__main__":
    main(sys.argv[1:])
