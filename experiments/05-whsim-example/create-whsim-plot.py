#!/usr/bin/env python3
import argparse
import sys

import pandas as pd
import matplotlib.pyplot as plt

from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))
from plt_commons import cm, measure_name_mapping, extract_measures_from_config


def parse_args(args):
    parser = argparse.ArgumentParser(
        description="Analyze a DendroTime experiment and plot the weighted hierarchy similarity."
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
        choices=["dtw", "msm", "sbd", "euclidean"],
        help="Distance measure",
    )
    parser.add_argument(
        "--linkage",
        type=str,
        default="average",
        choices=["single", "complete", "average", "weighted", "ward"],
        help="Linkage method",
    )
    parser.add_argument(
        "--strategy",
        type=str,
        default="approx-distance-ascending",
        choices=["fcfs", "pre-clustering", "approx-distance-ascending"],
        help="Strategy name",
    )

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    if args.resultfile is not None:
        results_file = Path(args.resultfile)
    elif args.dataset is None:
        raise ValueError("Either provide the result file or the dataset name.")
    else:
        dataset = args.dataset
        distance = args.distance
        linkage = args.linkage
        strategy = args.strategy
        results_file = Path(
            f"results/{dataset}-{distance}-{linkage}-{strategy.replace('-', '_')}/Finished-100/qualities.csv"
        )

    results_file = results_file.resolve()
    if not results_file.exists():
        raise FileNotFoundError(f"Result file {results_file} not found!")

    plot_results(results_file)


def plot_results(results_file, include_steps=False):
    # experiment details
    parts = results_file.parent.parent.stem.split("-")
    dataset = parts[0]
    distance = parts[1]
    linkage = parts[2]
    strategy = parts[3]

    # measure details
    config_file = results_file.parent / "config.json"
    measures = extract_measures_from_config(config_file)

    if measures["hierarchy-quality"] != "weightedHierarchySimilarity":
        raise ValueError(
            "Only 'weightedHierarchySimilarity' is supported as hierarchy similarity "
            f"measure, but got '{measures['hierarchy-quality']}' instead!"
        )

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
    change_point = df["index"].max() // 2
    change_point_runtime = df.loc[df["index"] >= change_point, "timestamp"].iloc[0]
    print(
        f"Phase change at index {change_point} and runtime {change_point_runtime:.0f}"
    )

    aucs = df.sum(axis=0) / df.shape[0]
    runtime_auc = (
        df["hierarchy-quality"] * df["timestamp"].diff().fillna(0).shift(-1)
    ).sum() / df["timestamp"].max()
    step_auc = (
        df["hierarchy-quality"] * df["index"].diff().fillna(0).shift(-1)
    ).sum() / df["index"].max()
    print(f"Simple AUC={aucs['hierarchy-quality']:0.2f}")
    print(f"Runtime AUC={runtime_auc:0.2f}")
    print(f"Step AUC={step_auc:0.2f}")

    n_cols = 2 if include_steps else 1
    fig, axs = plt.subplots(
        1, n_cols, squeeze=False, figsize=(3.5 * n_cols, 2), sharey="all"
    )
    axs = axs[0, :]

    # runtime plot
    axs[0].grid(visible=True, which="major", axis="y", linestyle="dotted", linewidth=1)
    axs[0].axvline(
        x=change_point_runtime,
        color="gray",
        linestyle="--",
        label="Approx. $\\rightarrow$ Exact",
    )
    axs[0].step(
        df["timestamp"],
        df["hierarchy-quality"],
        where="post",
        label=measure_name_mapping[measures["hierarchy-quality"]],
        color=cm(2),
        lw=2,
    )
    axs[0].fill_between(
        df["timestamp"],
        df["hierarchy-quality"],
        alpha=0.2,
        step="post",
        color=cm(2),
    )
    axs[0].text(
        0.6 * df["timestamp"].max(),
        0.25,
        f"AUC: {runtime_auc:.2f}",
        fontweight="bold",
    )
    axs[0].set_xlabel(f"Runtime ({runtime_unit})")
    axs[0].set_ylim(0.0, 1.05)
    axs[0].set_ylabel("Quality")

    # step plot
    if include_steps:
        axs[1].grid(
            visible=True, which="major", axis="y", linestyle="dotted", linewidth=1
        )
        axs[1].axvline(
            x=change_point,
            color="gray",
            linestyle="--",
            label="Approx. $\\rightarrow$ Exact",
        )
        axs[1].step(
            df["index"],
            df["hierarchy-quality"],
            where="post",
            label=measure_name_mapping[measures["hierarchy-quality"]],
            color=cm(2),
            lw=2,
        )
        axs[1].fill_between(
            df["index"],
            df["hierarchy-quality"],
            alpha=0.2,
            step="post",
            color=cm(2),
        )
        axs[1].text(
            0.6 * df["index"].max(),
            0.25,
            f"AUC: {step_auc:.2f}",
            fontweight="bold",
        )
        axs[1].set_xlabel("Computational steps")

    handles, labels = axs[0].get_legend_handles_labels()
    if include_steps:
        fig.legend(
            handles,
            labels,
            ncol=len(handles),
            loc="upper center",
            bbox_to_anchor=(0.5, 1.06),
        )
    else:
        fig.legend(
            handles,
            labels,
            ncol=1,
            loc="center left",
            bbox_to_anchor=(0.95, 0.5),
        )
    plt.savefig(
        f"whsim-{dataset}-{distance}-{linkage}-{strategy}.pdf",
        bbox_inches="tight",
    )


if __name__ == "__main__":
    main(sys.argv[1:])
