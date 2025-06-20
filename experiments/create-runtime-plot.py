import sys
import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

sys.path.append(str(Path(__file__).parent.parent))
from plt_commons import colors, markers, strategy_name, distance_name
from download_datasets import LONG_RUNNING_DATASETS, select_aeon_datasets

selected_strategies = (
    "approx_distance_ascending",
    "pre_clustering",
    "fcfs",
)
# took too long!
finally_excluded_datasets = [
    "SemgHandSubjectCh2",
    "EthanolLevel",
    "HandOutlines",
    "CinCECGTorso",
    "Phoneme",
    "Mallat",
    "MixedShapesRegularTrain",
    "MixedShapesSmallTrain",
    "FordA",
    "FordB",
    "NonInvasiveFetalECGThorax1",
    "NonInvasiveFetalECGThorax2",
    "UWaveGestureLibraryAll",
    "UWaveGestureLibraryX",
    "UWaveGestureLibraryY",
    "UWaveGestureLibraryZ",
    "Yoga",
    "Crop",
    "ElectricDevices",
    "StarLightCurves",
]
distance_order = ["euclidean", "lorentzian", "sbd", "dtw", "msm", "kdtw"]
kdtw_datasets = select_aeon_datasets(download_all=True, sorted=True)[:100]


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--show-jet-variance",
        action="store_true",
        help="Show variance of JET runtime and WHS",
    )
    parser.add_argument(
        "--distances",
        nargs="*",
        default=distance_order,
        help="Distances to include in the plot (default: all)",
    )
    parser.add_argument(
        "--include-ward", action="store_true", help="Include ward linkage"
    )
    parser.add_argument(
        "--disable-variances", action="store_true", help="Disable variance plotting"
    )
    parser.add_argument(
        "--extend-strategy-runtimes",
        action="store_true",
        help="Extend strategy runtimes to the right until the maximum runtime",
    )
    parser.add_argument(
        "-c",
        "--correct-dendrotime-runtime",
        action="store_true",
        help="Correct dendrotime runtime by removing quality measurement overhead",
    )
    parser.add_argument(
        "--limit-kdtw-datasets",
        action="store_true",
        help="Limit kdtw to the first 100 datasets (for performance reasons)",
    )
    parser.add_argument(
        "--alternative-legend",
        action="store_true",
        help="Plot legend above the plot instead of on the right",
    )
    return parser.parse_args()


def main(
    selected_distances,
    show_jet_variance=False,
    include_ward=False,
    disable_variances=False,
    extend_strategy_runtimes=False,
    correct_dendrotime_runtime=False,
    limit_kdtw_datasets=False,
    alternative_legend=False,
):
    # load results from serial execution
    # df_serial = pd.read_csv("01-serial-hac/results/aggregated-runtimes.csv")
    # df_serial["strategy"] = "serial"
    # df_serial = df_serial[df_serial["phase"] == "Finished"]
    # df_serial = df_serial.drop(columns=["phase"])
    # df_serial["whs"] = 1.0

    # load results from jet execution
    df_jet = pd.read_csv("06-jet/results/results.csv")
    df_jet["strategy"] = "JET"
    # df_jet["distance"] = np.tile(["sbd", "msm", "dtw"], df_jet.shape[0] // 3)
    df_jet.replace(-1, np.nan, inplace=True)
    # JET does only support ward linkage:
    df_jet["linkage"] = "ward"

    # load results from parallel execution
    df_parallel = pd.read_csv("07-parallel-hac/results/aggregated-runtimes.csv")
    df_parallel["strategy"] = "parallel"
    df_parallel = df_parallel[df_parallel["phase"] == "Finished"]
    df_parallel = df_parallel.drop(columns=["phase"])
    df_parallel["whs"] = 1.0

    # load results from system execution
    df_dendrotime = pd.read_csv("04-dendrotime/results/aggregated-runtimes.csv")
    df_dendrotime["runtime_1.0"] = df_dendrotime["finished"]
    df_dendrotime = df_dendrotime.drop(
        columns=[
            "initializing",
            "approximating",
            "computingfulldistances",
            "finalizing",
            "finished",
        ]
    )
    runtime_cols = [c for c in df_dendrotime.columns if c.startswith("runtime")]
    if correct_dendrotime_runtime:
        # --- runtime correction (remove quality measurement overhead)
        df_dendrotime_nqa = pd.read_csv(
            "08-dendrotime-no-quality/results/aggregated-runtimes.csv"
        )
        df_dendrotime_nqa["runtime_nqa"] = df_dendrotime_nqa["finished"]
        df_dendrotime_nqa = df_dendrotime_nqa.drop(
            columns=[
                "initializing",
                "approximating",
                "computingfulldistances",
                "finalizing",
                "finished",
            ]
        )
        df_dendrotime_nqa = df_dendrotime_nqa.set_index(
            ["dataset", "distance", "linkage", "strategy"]
        ).sort_index()
        df_tmp = df_dendrotime.set_index(
            ["dataset", "distance", "linkage", "strategy"]
        ).sort_index()
        df_tmp = pd.merge(
            df_tmp, df_dendrotime_nqa, how="left", left_index=True, right_index=True
        )
        df_tmp["runtime_correction_factor"] = (
            df_tmp["runtime_1.0"] / df_tmp["runtime_nqa"]
        )
        df_tmp["runtime_correction_factor"] = df_tmp[
            "runtime_correction_factor"
        ].fillna(1.0)
        for c in runtime_cols:
            df_tmp[c] = df_tmp[c] / df_tmp["runtime_correction_factor"]
        df_dendrotime = df_tmp.drop(
            columns=["runtime_nqa", "runtime_correction_factor"]
        ).reset_index()
        # --- end runtime correction

    df_dendrotime = df_dendrotime.melt(
        id_vars=["dataset", "distance", "linkage", "strategy"],
        var_name="whs",
        value_vars=runtime_cols,
        value_name="runtime",
        ignore_index=True,
    )
    df_dendrotime["whs"] = (
        df_dendrotime["whs"].str.replace("runtime_", "").astype(float)
    )

    df = pd.concat([df_jet, df_dendrotime, df_parallel], ignore_index=True)
    df["runtime"] = df["runtime"] / 1000  # convert to seconds

    # only consider datasets with parallel runtime >= 5 minutes
    # print(df[(df["strategy"] == "parallel") & (df["distance"] == "msm")])
    # datasets = df.loc[(df["strategy"] == "parallel") & (df["distance"] == "msm") & (df["runtime"] < 5*60), "dataset"].unique()
    # print("Datasets", len(datasets))
    # df = df[df["dataset"].isin(datasets)]

    # convert runtime to relative to parallel runtimes
    for _, group in df.groupby(["dataset", "distance", "linkage"]):
        try:
            parallel_runtime = group.loc[
                group["strategy"] == "parallel", "runtime"
            ].item()
        except ValueError:
            parallel_runtime = np.nan
        df.loc[group.index, "runtime"] = group["runtime"] / parallel_runtime

    distances = df["distance"].unique().tolist()
    distances = [d for d in distances if d in selected_distances]
    distances = sorted(distances, key=lambda x: distance_order.index(x))
    linkages = set(df["linkage"].unique())
    if not include_ward:
        linkages = linkages - {"ward"}
    linkages = sorted(linkages)
    # df = df[
    #     (df["distance"].isin(distances))
    #     & (df["linkage"].isin(linkages))
    # ]

    if limit_kdtw_datasets:
        # for the kdtw distance, we only consider the first 100 datasets
        df = df[
            (df["distance"] != "kdtw")
            | ~(df["dataset"].isin(finally_excluded_datasets))
        ]

    dataset_count = df[(df["whs"] == 1.0) | (df["strategy"] == "JET")].groupby(["strategy", "distance", "linkage"])["dataset"].count()
    print(f"Distances: {distances}")
    print(f"Linkages: {linkages}")
    with pd.option_context("display.max_rows", None, "display.max_columns", None):
        print(f"Processed datasets: {dataset_count}")
    print(df[(df["dataset"] == "ACSF1") & (df["whs"] == 1.0)].sort_values(["strategy", "distance", "linkage"]))

    # use right y ticks and labels for this plot
    plt.rcParams["ytick.right"] = plt.rcParams["ytick.labelright"] = True
    plt.rcParams["ytick.left"] = plt.rcParams["ytick.labelleft"] = False
    plt.rcParams["lines.markersize"] = 4

    fig, axs = plt.subplots(
        len(linkages),
        len(distances),
        figsize=(10, 3),
        sharex="none",
        sharey="none",
        constrained_layout=False,
        gridspec_kw={"hspace": 0.75, "wspace": 0.2},
    )
    # configure labels and headers
    for i, linkage in enumerate(linkages):
        rowHeaderAx = axs[i, 0].twinx()
        rowHeaderAx.yaxis.set_label_position("left")
        rowHeaderAx.set_yticks([])
        rowHeaderAx.set_yticklabels([])
        rowHeaderAx.set_ylabel(linkage, size="large", rotation=0, ha="right", va="center")
        for spine in rowHeaderAx.spines.values():
            spine.set_visible(False)
        axs[i, 0].yaxis.set_label_position("right")
        axs[i, 0].yaxis.set_ticks_position("right")
        axs[i, 0].yaxis.tick_right()

        for j in range(axs.shape[1]):
            # helper grid line:
            axs[i, j].axvline(x=1, color="lightgray", ls="--", lw=1)
            axs[i, j].tick_params(labelbottom=False)
            axs[i, j].set_ylim(-0.05, 1.1)
            axs[i, j].set_yticks([0.0, 0.5, 1.0])
            axs[i, j].set_yticklabels([])
            axs[i, j].yaxis.set_label_position("right")
            axs[i, j].spines["left"].set_visible(False)
            axs[i, j].spines["top"].set_visible(False)

        axs[i, -1].set_ylabel("WHS")
        axs[i, -1].set_yticklabels([0.0, 0.5, 1.0])

    for j, distance in enumerate(distances):
        title = distance_name(distance)
        if limit_kdtw_datasets and distance == "kdtw":
            title += "$^*$"
        axs[0, j].set_title(title, size="large")
        axs[-1, j].tick_params(labelbottom=True)
        axs[-1, j].set_xlabel("relative runtime")

    # add plots
    handles, labels = [], []
    for j, distance in enumerate(distances):
        # aggregate WHS and runtime over datasets to get a single point for JET
        # JET only supports ward linkage, so broadcast to all linkages
        df_jet = df[
            (df["linkage"] == "ward")
            & (df["distance"] == distance)
            & (df["strategy"] == "JET")
        ]
        df_jet = df_jet[["whs", "runtime"]].agg(["mean", "median", "std"], axis=0)

        # get maximum runtime for scaling and extending strategy lines to right
        jet_whs = df_jet.loc["mean", "whs"]
        jet_runtime = df_jet.loc["mean", "runtime"]
        # same across all linkages, so we just need axis ticks once
        max_other_runtime = (
            df[(df["distance"] == distance) & (df["strategy"] != "JET")]
                .groupby(["strategy", "linkage", "whs"])["runtime"]
                .mean()
                .max()
        )
        max_runtime = max(jet_runtime, max_other_runtime)

        for i, linkage in enumerate(linkages):
            ax = axs[i, j]
            ax_jet = ax

            # compute mean and std runtime for each strategy over the datasets
            df_filtered = df[
                (df["linkage"] == linkage)
                & (df["distance"] == distance)
                & (df["strategy"] != "JET")
            ]
            df_filtered = (
                df_filtered.groupby(["strategy", "whs"])[["runtime"]]
                .agg(["mean", "std"])
                .reset_index()
                .set_index("strategy")
            )

            # cut axis, where JET runtime is large into two
            break_point = max(max_other_runtime, 2.1)
            break_axis = jet_runtime > break_point
            if break_axis:
                # we get the gridspec of the axis, remove the axis, add a subgridspec to
                # it, and then add two new axes to it
                kwargs = dict(wspace=0.05, hspace=0.0)
                gs = ax.get_subplotspec().subgridspec(1, 2, width_ratios=[3, 1], **kwargs)
                ax_default = fig.add_subplot(gs[0, 0])
                ax_jet = fig.add_subplot(gs[0, 1])
                # remove old axis (at least the visible parts)
                # ax.remove()
                for spine in ax.spines.values():
                    spine.set_visible(False)
                ax.tick_params(colors="white")
                ax.set_yticks([])
                ax.set_yticklabels([])
                ax.set_ylabel("")

                # reconfigure the default axis
                ax_default.set_xlim(-0.1, break_point)
                ax_default.set_xticks([0.0, 1.0, 2.0])
                ax_default.tick_params(labelbottom=False)
                ax_default.set_ylim(-0.05, 1.1)
                ax_default.set_yticks([])
                ax_default.set_yticks([], minor=True)
                ax_default.set_yticklabels([])
                ax_default.yaxis.set_ticks_position("none")
                ax_default.tick_params(labelright=False, labelleft=False)
                ax_default.axvline(x=1, color="lightgray", ls="--", lw=1)
                ax_default.spines["right"].set_visible(False)
                ax_default.spines["left"].set_visible(False)
                ax_default.spines["top"].set_visible(False)

                # still included from the old axis:
                # if i == 0:
                #     ax_default.set_title(linkage, size="large")
                # if i == len(distances) - 1:
                #     ax_default.set_xlabel("relative runtime")

                # configure the JET axis
                ax_jet.set_ylim(-0.05, 1.1)
                ax_jet.set_yticks([0.0, 0.5, 1.0])
                ax_jet.yaxis.set_label_position("right")

                if j == len(distances) - 1:
                    ax_jet.set_ylabel("WHS")
                    ax_jet.set_yticklabels([0.0, 0.5, 1.0])
                    ax_jet.tick_params(labelright=True, labelleft=False)
                else:
                    ax_jet.set_yticklabels([])
                    ax_jet.tick_params(labelright=False, labelleft=False)

                # adjust y-axis limits to show JET point (zooms)
                ax_jet.set_xlim(break_point*1.1, 1.1*max_runtime)
                ax_jet.set_xticks([jet_runtime])
                ax_jet.set_xticklabels([f"{jet_runtime:.1f}"])
                ax_jet.tick_params(labelbottom=False)
                ax_jet.spines["left"].set_visible(False)
                ax_jet.spines["top"].set_visible(False)

                if i == len(linkages) - 1:
                    ax_default.tick_params(labelbottom=True)
                    ax_jet.tick_params(labelbottom=True)

                # add slanted lines to indicate the break
                d = .75  # proportion of vertical to horizontal extent of the slanted line
                kwargs = dict(marker=[(-1, -d), (1, d)], markersize=6,
                              linestyle="none", color='k', mec='k', mew=1, clip_on=False)
                # ax_jet.plot([0, 0], [0, 1], transform=ax_jet.transAxes, **kwargs)
                # ax_default.plot([1, 1], [0, 1], transform=ax_default.transAxes, **kwargs)
                ax_jet.plot([0], [0], transform=ax_jet.transAxes, **kwargs)
                ax_default.plot([1], [0], transform=ax_default.transAxes, **kwargs)
                ax = ax_default  # use the new axis for plotting

            # add plots for all strategies
            for strategy in selected_strategies[::-1]:
                if strategy not in df_filtered.index.get_level_values("strategy"):
                    print(f"Skipping {strategy} for {distance}-{linkage}")
                    continue
                color = colors[strategy]
                runtimes = df_filtered.loc[strategy, ("runtime", "mean")].values
                stddevs = df_filtered.loc[strategy, ("runtime", "std")].values
                whss = df_filtered.loc[strategy, "whs"].values
                if extend_strategy_runtimes:
                    runtimes = np.r_[
                        0.0, df_filtered.loc[strategy, ("runtime", "mean")], max_runtime
                    ]
                    stddevs = np.r_[
                        0.0, df_filtered.loc[strategy, ("runtime", "std")], 0.0
                    ]
                    whss = np.r_[0.0, df_filtered.loc[strategy, "whs"], 1.0]
                else:
                    runtimes = np.r_[0.0, runtimes]
                    stddevs = np.r_[0.0, stddevs]
                    whss = np.r_[0.0, whss]

                ax.plot(runtimes, whss, color=color)
                marker = markers[strategy]
                ax.plot(
                    runtimes[-1],
                    whss[-1],
                    color=color,
                    label=f"DendroTime {strategy_name(strategy)}",
                    marker=marker,
                    zorder=2.5,
                    clip_on=False
                )
                if not disable_variances:
                    ax.fill_betweenx(
                        whss,
                        runtimes - stddevs,
                        runtimes + stddevs,
                        color=color,
                        alpha=0.1,
                    )
                    # ax.errorbar(
                    #     runtimes,
                    #     whss,
                    #     xerr=stddevs,
                    #     label=strategy_name(strategy),
                    #     color=color,
                    #     lw=2,
                    #     elinewidth=1,
                    #     capsize=2,
                    # )
                if extend_strategy_runtimes and break_axis:
                    # extend the strategy line to the right
                    ax_jet.plot(runtimes, whss, color=color)
                    ax_jet.plot(
                        runtimes[-1],
                        whss[-1],
                        color=color,
                        label=strategy_name(strategy),
                        marker=marker,
                        zorder=2.5,
                        clip_on=False
                    )
                    if not disable_variances:
                        ax_jet.fill_betweenx(
                            whss,
                            runtimes - stddevs,
                            runtimes + stddevs,
                            color=color,
                            alpha=0.1,
                        )


            # add plot for parallel
            strategy = "parallel"
            color = colors[strategy]
            ax.scatter(
                df_filtered.loc[strategy, ("runtime", "mean")],
                df_filtered.loc[strategy, "whs"],
                label=strategy_name(strategy),
                color=color,
                marker=markers[strategy],
                zorder=2.5,
            )

            # add plot for JET
            strategy = "JET"
            color = colors[strategy]
            if show_jet_variance:
                ax_jet.errorbar(
                    jet_runtime,
                    jet_whs,
                    xerr=df_jet.loc["std", "runtime"],
                    yerr=df_jet.loc["std", "whs"],
                    label=strategy_name(strategy),
                    color=color,
                    marker=markers[strategy],
                    lw=2,
                    elinewidth=1,
                    capsize=2,
                )
                if break_axis:
                    ax.errorbar(
                        jet_runtime,
                        jet_whs,
                        xerr=df_jet.loc["std", "runtime"],
                        yerr=df_jet.loc["std", "whs"],
                        label=strategy_name(strategy),
                        color=color,
                        marker=markers[strategy],
                        lw=2,
                        elinewidth=1,
                        capsize=2,
                    )
            else:
                ax_jet.scatter(
                    jet_runtime,
                    jet_whs,
                    label=strategy_name(strategy),
                    color=color,
                    marker=markers[strategy],
                    zorder=2.5,
                )
            if i == 0 and j == 0:
                handles, labels = ax.get_legend_handles_labels()
                if break_axis:
                    # add JET to the legend
                    jet_handles, jet_labels = ax_jet.get_legend_handles_labels()
                    handles.extend(jet_handles)
                    labels.extend(jet_labels)

    # add legend
    if alternative_legend:
        legend = fig.legend(
            handles,
            labels,
            loc="upper center",
            ncol=len(handles),
            bbox_to_anchor=(0.5, 1.1),
            borderpad=0.25,
            handletextpad=0.4,
            columnspacing=1.0,
        )
    else:
        legend = fig.legend(
            handles,
            labels,
            loc="center left",
            ncol=1,
            bbox_to_anchor=(0.97, 0.5),
            borderpad=0.25,
            handletextpad=0.4,
            columnspacing=1.0,
        )
    fig.savefig(
        "mean-runtime-qualities.pdf", bbox_inches="tight", bbox_extra_artists=[legend]
    )
    fig.savefig(
        "mean-runtime-qualities.png", bbox_inches="tight", bbox_extra_artists=[legend]
    )
    # plt.show()


if __name__ == "__main__":
    args = parse_args()
    main(
        selected_distances=args.distances,
        show_jet_variance=args.show_jet_variance,
        include_ward=args.include_ward,
        disable_variances=args.disable_variances,
        correct_dendrotime_runtime=args.correct_dendrotime_runtime,
        extend_strategy_runtimes=args.extend_strategy_runtimes,
        limit_kdtw_datasets=args.limit_kdtw_datasets,
        alternative_legend=args.alternative_legend,
    )
