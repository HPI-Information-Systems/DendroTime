#!/usr/bin/env python3
import argparse
import sys

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parent.parent))
from plt_commons import colors, strategy_name


ariQualityMeasures = ("ari", "ariAt", "averageAri", "approxAverageAri")
selected_strategies = (
    "preClustering",
    "approxAscending",
    "approxFullError",
    "shortestTs",
    "fcfs",
)
histogram_bins = 10


def parse_args(args):
    parser = argparse.ArgumentParser(
        description="Plot the convergence of the DendroTime strategies."
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
    n = len(strategy_files)
    if n < 1:
        raise ValueError("There are no 'strategies-*.csv'-files in the result folder!")

    fig, axs = plt.subplots(
        2,
        n,
        sharey="all",
        figsize=(10, 4),
        squeeze=False,
        constrained_layout=True,
    )
    for j, results_file in enumerate(strategy_files):
        dataset_name = results_file.stem.split("-")[1]
        suffix = "-".join(results_file.stem.split("-")[1:])
        traces_file = results_file.parent / f"traces-{suffix}.csv"
        timestamps_file = results_file.parent / f"timestamps-{suffix}.csv"
        strategiesPath = results_file.parent / f"strategies-{suffix}.csv"

        df_strategies = pd.read_csv(results_file)
        max_index = df_strategies["index"].max()
        traces = np.genfromtxt(traces_file, delimiter=",")[: max_index + 1, :]
        runtimes = np.genfromtxt(timestamps_file, delimiter=",")[: max_index + 1, :]
        # convert timestamps to relative runtimes
        runtimes = runtimes - runtimes[:, [0]]
        # convert to seconds
        runtimes /= 1000

        axs[0, j].set_title(dataset_name)
        axs[0, j].grid(
            visible=True, which="major", axis="y", linestyle="dotted", linewidth=1
        )
        axs[0, j].set_xlabel("Runtime (s)")
        axs[0, j].set_ylim(0.0, 1.05)
        axs[0, j].set_ylabel("WHS")
        axs[1, j].grid(
            visible=True, which="major", axis="y", linestyle="dotted", linewidth=1
        )
        axs[1, j].set_xlabel("Computational steps")
        axs[0, j].set_ylim(0.0, 1.05)
        axs[0, j].set_ylabel("WHS")

        print(f"\n{dataset_name}")
        print(f"{'=' * 11} AUC: simple runtime step")
        for strategy in selected_strategies:
            row = df_strategies.loc[df_strategies["strategy"] == strategy]
            if row.empty:
                continue
            strategy = row["strategy"].item()
            auc = row["quality"].item()
            index = row["index"].item()
            color = colors[strategy]
            #             trace_x = runtimes[index, :]
            #             trace_y = traces[index, :]
            #             indices = np.arange(trace_x.shape[0])
            trace_x = np.r_[runtimes[index, :], np.max(runtimes)]
            trace_y = np.r_[traces[index, :], 1.0]
            indices = np.arange(trace_x.shape[0])

            runtime_auc = (trace_y[:-1] * np.diff(trace_x, 1)).sum() / trace_x.max()
            step_auc = (trace_y[:-1] * np.diff(indices, 1)).sum() / indices.max()
            print(
                f"{strategy.ljust(16)}   {auc:.02f}    {runtime_auc:0.2f} {step_auc:0.2f}"
            )

            plot_traces(trace_x, trace_y, strategy, color, axs[:, j])

    handles, labels = axs[0, 0].get_legend_handles_labels()
    fig.legend(
        handles,
        labels,
        ncol=len(handles),
        loc="upper center",
        bbox_to_anchor=(0.5, 1.06),
    )

    #     plt.tight_layout()
    plt.show()


def plot_traces(timestamps, qualities, strategy, color, axs):
    indices = np.arange(timestamps.shape[0])
    # runtime plot
    axs[0].step(
        timestamps,
        qualities,
        where="post",
        label=strategy_name(strategy),
        color=color,
        lw=2,
    )
    axs[0].fill_between(
        timestamps,
        qualities,
        alpha=0.2,
        step="post",
        color=color,
    )
    #     axs[0].text(
    #         0.6 * timestamps.max(),
    #         0.25,
    #         f"AUC: {runtime_auc:.2f}",
    #         fontweight="bold",
    #         color=color,
    #     )

    # step plot
    axs[1].step(
        indices,
        qualities,
        where="post",
        label=strategy_name(strategy),
        color=color,
        lw=2,
    )
    axs[1].fill_between(
        indices,
        qualities,
        alpha=0.2,
        step="post",
        color=color,
    )


#     axs[1].text(
#         0.6 * indices.max(),
#         0.25,
#         f"AUC: {step_auc:.2f}",
#         fontweight="bold",
#     )


if __name__ == "__main__":
    main(sys.argv[1:])
