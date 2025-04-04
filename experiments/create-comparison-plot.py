#!/usr/bin/env python3
import argparse
import sys

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parent.parent))
from plt_commons import colors, markers, strategy_name, phase_name, dataset_name

RESULT_FOLDER = Path("04-dendrotime/results")
NQA_RESULT_FOLDER = Path("08-dendrotime-no-quality/results")
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
    parser.add_argument(
        "-c",
        "--correct-dendrotime-runtime",
        action="store_true",
        help="Correct dendrotime runtime by removing quality measurement overhead",
    )

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    experiment1 = args.experiment1
    experiment2 = args.experiment2
    include_baselines = args.include_baselines
    highlight_phase = args.highlight_phase
    use_no_quality_for_runtimes = args.correct_dendrotime_runtime

    print(f"Creating comparison plot between {experiment1} and {experiment2}")

    print("  loading data for selected DendroTime strategies ...")
    # load runtime breakdown
    result_file = (
        NQA_RESULT_FOLDER if use_no_quality_for_runtimes else RESULT_FOLDER
    ) / "aggregated-runtimes.csv"
    if not result_file.exists():
        raise ValueError(f"Runtime file '{result_file}' does not exist!")
    df_runtime = pd.read_csv(result_file).set_index(
        ["dataset", "distance", "linkage", "strategy"]
    )
    df_runtime = df_runtime[
        [
            "initializing",
            "approximating",
            "computingfulldistances",
            "finalizing",
            "finished",
        ]
    ]
    df_runtime.columns = [
        "Initializing",
        "Approximating",
        "ComputingFullDistances",
        "Finalizing",
        "Finished",
    ]

    traces = []
    runtimes = []
    for experiment_config in [experiment1, experiment2]:
        for strategy in selected_strategies:
            # load runtime breakdown
            s_runtime = df_runtime.loc[
                (*experiment_config.split("-"), strategy), :
            ].copy()
            s_runtime = s_runtime.astype(float)
            # convert to seconds
            for c in s_runtime.index:
                s_runtime[c] /= 1000
            # make relative
            for c in s_runtime.index:
                if c == "Finished":
                    continue
                s_runtime[c] = s_runtime[c] / s_runtime["Finished"]
            s_runtime.name = (experiment_config, strategy)
            runtimes.append(s_runtime)

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

            if use_no_quality_for_runtimes:
                results_file = NQA_RESULT_FOLDER / "aggregated-runtimes.csv"
                df_nqa = pd.read_csv(results_file)
                df_nqa = df_nqa.set_index(
                    ["dataset", "distance", "linkage", "strategy"]
                )

            df = pd.read_csv(trace_file)
            df["experiment"] = experiment_config
            df["strategy"] = strategy
            df["runtime"] = df["timestamp"] - df["timestamp"].min()
            df["runtime"] /= 1000  # convert to seconds
            if use_no_quality_for_runtimes:
                # correct runtime by removing quality measurement overhead
                factor = df["runtime"].max() / s_runtime["Finished"]
                print(f"Correcting runtime by factor {factor:.2f}")
                df["runtime"] = df["runtime"] / factor

            df = df[["experiment", "strategy", "runtime", "hierarchy-quality"]]
            traces.append(df)

    df = pd.concat(traces, ignore_index=True)
    df_runtimes = pd.DataFrame(runtimes)
    df_runtimes.index.names = ["experiment", "strategy"]
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
                        ]
                        / 1000,
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
        figsize=(5.5, 1.5),
        constrained_layout=True,
        gridspec_kw={"width_ratios": [3, 1, 3, 1]},
    )
    for i, experiment_config in enumerate([experiment1, experiment2]):
        ax = axs[i * 2]

        # set title in an invisible axis
        title_ax = fig.add_subplot(1, 2, i + 1, frame_on=False)
        title_ax.set_xticks([])
        title_ax.set_yticks([])
        dataset, distance, linkage = experiment_config.split("-")
        title_ax.set_title(f"{dataset_name(dataset)}-{distance}-{linkage}")
        # hack to let the contrained_layout allocate space for our title (in the invisible axis)
        ax.set_title(" ")

        ax.grid(visible=True, which="major", axis="y", linestyle="dotted", linewidth=1)
        ax.set_xlabel("Runtime (s)")
        ax.set_ylim(0.0, 1.05)
        ax.set_yticks([0.0, 0.5, 1.0])
        ax.set_yticklabels([0.0, 0.5, 1.0])

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
                    marker=markers[strategy],
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
            # annotate highlighted phase time
            if highlight_phase is not None and phase == highlight_phase:
                y = runtimes[phase]
                y_pos = bottom + y / 2
                ax.text(
                    0,
                    y_pos,
                    f"{y:.0%}",
                    color="black",
                    fontweight="bold",
                    va="center",
                    ha="center",
                )
            bottom += runtimes[phase]
        ax.set_ylabel("Rel. Runtime")
        # ax.set_xlabel(f"{runtimes['Finished']:.0f} s")
        ax.set_xticks([])
        ax.set_xticklabels([])
        ax.set_yticks([])
        ax.set_yticklabels([])

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
