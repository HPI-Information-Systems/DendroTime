#!/usr/bin/env python3
import argparse
import sys

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parent.parent))
from plt_commons import colors, strategy_name

RESULT_FOLDER = Path("results")
selected_strategies = (
    "fcfs",
    # "pre_clustering",
    "approx_distance_ascending",
    # "shortestTs",
)


def parse_args(args):
    parser = argparse.ArgumentParser(
        description="Plot the convergence of the DendroTime strategies for a specific experiment."
    )
    parser.add_argument(
        "--dataset",
        type=str,
        help="The dataset name to plot.",
    )
    parser.add_argument(
        "--distance",
        type=str,
        default="msm",
        help="The distance measure used.",
    )
    parser.add_argument(
        "--linkage",
        type=str,
        default="weighted",
        help="The linkage method used.",
    )

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    dataset = args.dataset
    distance = args.distance
    linkage = args.linkage

    print(f"Creating plot for {dataset} {distance} {linkage}")

    traces = []
    for strategy in selected_strategies:
        # check for trace file!
        trace_file = (
            RESULT_FOLDER
            / f"{dataset}-{distance}-{linkage}-{strategy}"
            / "Finished-100"
            / "qualities.csv"
        )
        if not trace_file.exists():
            print(f"Trace file '{trace_file}' does not exist!", file=sys.stderr)
            continue

        df = pd.read_csv(trace_file)
        df["strategy"] = strategy
        df["runtime"] = df["timestamp"] - df["timestamp"].min()
        df["runtime"] /= 1000  # convert to seconds
        df = df[["strategy", "runtime", "hierarchy-quality"]]
        traces.append(df)

    df = pd.concat(traces, ignore_index=True)

    max_runtime = df["runtime"].max()

    fig, ax = plt.subplots(figsize=(10, 4), constrained_layout=True)
    ax.set_title(f"{dataset} {distance} {linkage}")
    ax.grid(visible=True, which="major", axis="y", linestyle="dotted", linewidth=1)
    ax.set_xlabel("Runtime (s)")
    ax.set_ylim(0.0, 1.05)
    ax.set_ylabel("WHS")

    for strategy in selected_strategies:
        df_strategy = df[df["strategy"] == strategy]
        if df_strategy.empty:
            continue

        x = np.r_[0, df_strategy["runtime"], max_runtime]
        y = np.r_[0, df_strategy["hierarchy-quality"], 1.0]
        runtime_auc = (y[:-1] * np.diff(x, 1)).sum() / max_runtime

        ax.step(
            x,
            y,
            where="post",
            label=f"{strategy_name(strategy)} AUC={runtime_auc:.2f}",
            color=colors[strategy],
            lw=2,
        )
        ax.fill_between(x, y, alpha=0.1, step="post", color=colors[strategy])
        # ax.text(
        #     0.6 * max_runtime,
        #     0.25,
        #     f"AUC: {runtime_auc:.2f}",
        #     color=colors[strategy],
        #     fontweight="bold",
        #     va="center",
        #     ha="right",
        # )

    ax.legend()
    # handles, labels = ax.get_legend_handles_labels()
    # fig.legend(
    #     handles,
    #     labels,
    #     ncol=len(handles),
    #     loc="upper center",
    #     bbox_to_anchor=(0.5, 1.06),
    # )

    #     plt.tight_layout()
    plt.show()


if __name__ == "__main__":
    main(sys.argv[1:])
