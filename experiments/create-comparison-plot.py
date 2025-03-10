#!/usr/bin/env python3
import argparse
import sys
from turtle import width
from xml.etree.ElementInclude import include

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parent.parent))
from plt_commons import colors, markers, strategy_name, phase_name

RESULT_FOLDER = Path("04-dendrotime/results")
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
        "experiment1",
        type=str,
        help="The first configuration to plot. Format: 'dataset-distance-linkage'.",
    )
    parser.add_argument(
        "experiment2",
        type=str,
        help="The second configuration to plot. Format: 'dataset-distance-linkage'.",
    )
    parser.add_argument(
        "--include-baselines",
        action="store_true",
        help="Include the parallel and JET baselines in the plot.",
    )
    parser.add_argument(
        "--highlight-phase",
        type=str,
        choices=[
            "Initializing",
            "Approximating",
            "ComputingFullDistances",
            "Finalizing",
            None,
        ],
        default=None,
        help="Display the relative runtime of a specific phase in the plot.",
    )

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    experiment1 = args.experiment1
    experiment2 = args.experiment2
    include_baselines = args.include_baselines
    highlight_phase = args.highlight_phase

    print(f"Creating comparison plot between {experiment1} and {experiment2}")

    print("  loading data for selected DendroTime strategies ...")
    traces = []
    runtimes = []
    for experiment_config in [experiment1, experiment2]:
        for strategy in selected_strategies:
            # check for trace file!
            trace_file = (
                RESULT_FOLDER
                / f"{experiment_config}-{strategy}"
                / "Finished-100"
                / "qualities.csv"
            ).resolve()
            if not trace_file.exists():
                print(f"Trace file '{trace_file}' does not exist!", file=sys.stderr)
                continue

            df = pd.read_csv(trace_file)
            df["experiment"] = experiment_config
            df["strategy"] = strategy
            df["runtime"] = df["timestamp"] - df["timestamp"].min()
            df["runtime"] /= 1000  # convert to seconds
            df = df[["experiment", "strategy", "runtime", "hierarchy-quality"]]
            traces.append(df)

            # check for runtime file!
            runtime_file = (
                RESULT_FOLDER
                / f"{experiment_config}-{strategy}"
                / "Finished-100"
                / "runtimes.csv"
            ).resolve()
            if not runtime_file.exists():
                print(f"Runtime file '{runtime_file}' does not exist!", file=sys.stderr)
                continue

            df_runtime = pd.read_csv(runtime_file).set_index("phase")
            df_runtime = df_runtime.T.reset_index(drop=True)
            # convert to seconds
            for c in df_runtime.columns:
                df_runtime[c] /= 1000
            # make relative
            for c in df_runtime.columns:
                if c == "Finished":
                    continue
                df_runtime[c] = df_runtime[c] / df_runtime["Finished"]
            df_runtime["experiment"] = experiment_config
            df_runtime["strategy"] = strategy
            runtimes.append(df_runtime)

    df = pd.concat(traces, ignore_index=True)
    df_runtimes = pd.concat(runtimes, ignore_index=True)
    df_runtimes = df_runtimes.set_index(["experiment", "strategy"])
    print("  ... done.")

    if include_baselines:
        print("  loading data for JET and parallel baselines ...")
        # load results from jet execution
        df_jet = pd.read_csv("06-jet/results/results.csv")
        df_jet["distance"] = np.tile(["sbd", "msm", "dtw"], df_jet.shape[0] // 3)
        df_jet.replace(-1, np.nan, inplace=True)
        df_jet = df_jet.set_index(["dataset", "distance"])

        # load results from parallel execution
        df_parallel = pd.read_csv("07-parallel-hac/results/aggregated-runtimes.csv")
        df_parallel = df_parallel[df_parallel["phase"] == "Finished"]
        df_parallel = df_parallel.drop(columns=["phase"])
        df_parallel["whs"] = 1.0
        df_parallel = df_parallel.set_index(["dataset", "distance", "linkage"])

        entries = []
        for experiment_config in [experiment1, experiment2]:
            dataset, distance, linkage = experiment_config.split("-")
            entries.extend(
                [
                    {
                        "strategy": "JET",
                        "experiment": experiment_config,
                        "runtime": df_jet.loc[(dataset, distance), "runtime"] / 1000,
                        "whs": df_jet.loc[(dataset, distance), "whs"],
                    },
                    {
                        "strategy": "parallel",
                        "experiment": experiment_config,
                        "runtime": df_parallel.loc[
                            (dataset, distance, linkage), "runtime"
                        ] / 1000,
                        "whs": 1.0,
                    },
                ]
            )
        df_baselines = pd.DataFrame(entries).set_index(["experiment", "strategy"])
        print(df_baselines)
        print("  ... done.")

    print("  plotting ...")
    max_runtimes = df.groupby(["experiment"])["runtime"].max()
    fig, axs = plt.subplots(
        1,
        4,
        sharey="all",
        figsize=(7, 2),
        constrained_layout=True,
        gridspec_kw={"width_ratios": [3, 1, 3, 1]},
    )
    for i, experiment_config in enumerate([experiment1, experiment2]):
        ax = axs[i * 2]

        # set title in an invisible axis
        title_ax = fig.add_subplot(1, 2, i + 1, frame_on=False)
        title_ax.set_xticks([])
        title_ax.set_yticks([])
        title_ax.set_title(f"{experiment_config}")
        # hack to let the contrained_layout allocate space for our title (in the invisible axis)
        ax.set_title(" ")

        ax.grid(visible=True, which="major", axis="y", linestyle="dotted", linewidth=1)
        ax.set_xlabel("Runtime (s)")
        ax.set_ylim(0.0, 1.05)
        if i == 1:
            # hack to increase spacing between the two experiment configurations
            ax.set_ylabel("\nWHS")
        else:
            ax.set_ylabel("WHS")

        for strategy in selected_strategies:
            df_strategy = df[
                (df["experiment"] == experiment_config) & (df["strategy"] == strategy)
            ]
            if df_strategy.empty:
                continue

            x = np.r_[0, df_strategy["runtime"], max_runtimes[experiment_config]]
            y = np.r_[0, df_strategy["hierarchy-quality"], 1.0]
            runtime_auc = (y[:-1] * np.diff(x, 1)).sum() / max_runtimes[
                experiment_config
            ]
            print(f"    {experiment_config} {strategy} AUC={runtime_auc:.2f}")

            ax.step(
                x,
                y,
                where="post",
                label=strategy_name(strategy),
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

        if include_baselines:
            for strategy in ["JET", "parallel"]:
                (runtime, whs) = df_baselines.loc[(experiment_config, strategy), :]
                ax.scatter(
                    runtime,
                    whs,
                    label=strategy_name(strategy),
                    color=colors[strategy],
                    marker=markers[strategy]
                )

        ax = axs[i * 2 + 1]
        runtimes = df_runtimes.loc[(experiment_config, "approx_distance_ascending"), :]
        if runtimes.empty:
            continue

        # plot breakdown of relative runtimes in a stacked bar chart
        bottom = 0
        for phase in runtimes.index:
            if phase == "Finished":
                continue
            ax.bar(
                0,
                runtimes[phase],
                label=phase_name(phase),
                bottom=bottom,
                color=colors[phase],
                width=0.5,
            )
            bottom += runtimes[phase]
        ax.set_ylabel("Relative Runtime")
        # ax.set_xlabel(f"{runtimes['Finished']:.0f} s")
        ax.set_xticks([])
        ax.set_xticklabels([])
        # annotate highlighted phase time
        if highlight_phase is not None:
            y = runtimes[highlight_phase]
            y_pos = y / 2
            ax.text(
                0,
                y_pos,
                f"{y:.0%}",
                color="black",
                fontweight="bold",
                va="center",
                ha="center",
            )

    handles, labels = axs[0].get_legend_handles_labels()
    handles2, labels2 = axs[1].get_legend_handles_labels()
    fig.legend(
        handles + handles2,
        labels + labels2,
        ncol=len(handles + handles2) // 2,
        loc="lower center",
        bbox_to_anchor=(0.5, 1.0),
    )
    print("  ... done.")

    fig.savefig(f"comparison_{experiment1}_{experiment2}.pdf", bbox_inches="tight")
    # plt.tight_layout()
    # plt.show()


if __name__ == "__main__":
    # Run with
    # - python create-comparison-plot.py UWaveGestureLibraryAll-msm-weighted UWaveGestureLibraryAll-sbd-weighted (Average as soon as results are ready)
    # - python create-comparison-plot.py Crop-dtw-weighted ElectricDevices-dtw-weighted --highlight-phase Finalizing --include-baselines
    main(sys.argv[1:])
