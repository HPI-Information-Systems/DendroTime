import sys
import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

sys.path.append(str(Path(__file__).parent.parent))
from plt_commons import colors, markers, strategy_name
from download_datasets import LONG_RUNNING_DATASETS

selected_strategies = (
    "approx_distance_ascending",
    "pre_clustering",
    "fcfs",
)
# variable length OR at least 500 TS points on average
selected_datasets = [
    "PickupGestureWiimoteZ", "ShakeGestureWiimoteZ", "BirdChicken", "BeetleFly", "edeniss20182020_vpd_anomalies", "edeniss20182020_co2_anomalies", "edeniss20182020_valve_anomalies", "edeniss20182020_level_anomalies", "edeniss20182020_volume_anomalies", "OliveOil", "edeniss20182020_par_anomalies", "edeniss20182020_rh_anomalies", "edeniss20182020_ec_anomalies", "edeniss20182020_ph_anomalies", "GestureMidAirD1", "GestureMidAirD2", "GestureMidAirD3", "Herring", "GesturePebbleZ1", "GesturePebbleZ2", "Car", "Lightning2", "ShapeletSim", "edeniss20182020_pressure_anomalies", "AllGestureWiimoteY", "AllGestureWiimoteZ", "AllGestureWiimoteX", "InsectEPGSmallTrain", "InsectEPGRegularTrain", "edeniss20182020_temp_anomalies", "Rock", "Worms", "WormsTwoClass", "Earthquakes", "ACSF1", "HouseTwenty", "PLAID", "Computers", "edeniss20182020_ics_anomalies", "Haptics", "LargeKitchenAppliances", "SmallKitchenAppliances", "ScreenType", "RefrigerationDevices", "ShapesAll", "PigArtPressure", "PigCVP", "PigAirwayPressure", "EOGHorizontalSignal", "EOGVerticalSignal", "InlineSkate", "SemgHandGenderCh2", "SemgHandMovementCh2", "SemgHandSubjectCh2", "EthanolLevel", "HandOutlines", "CinCECGTorso", "Phoneme", "Mallat", "MixedShapesRegularTrain", "MixedShapesSmallTrain", "FordA", "FordB", "NonInvasiveFetalECGThorax1", "NonInvasiveFetalECGThorax2", "UWaveGestureLibraryAll", "StarLightCurves"
]
distance_order = ["euclidean", "lorentzian", "sbd", "dtw", "msm", "kdtw"]


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--show-jet-variance",
        action="store_true",
        help="Show variance of JET runtime and WHS",
    )
    parser.add_argument(
        "--include-euclidean", action="store_true", help="Include euclidean distance"
    )
    parser.add_argument(
        "--include-lorentzian", action="store_true", help="Include lorentzian distance"
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
    return parser.parse_args()


def main(
    show_jet_variance=False,
    include_euclidean=False,
    include_lorentzian=False,
    include_ward=False,
    disable_variances=False,
    extend_strategy_runtimes=False,
    correct_dendrotime_runtime=False,
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

    # load results from parallel execution
    df_parallel = pd.read_csv("07-parallel-hac/results/aggregated-runtimes.csv")
    df_parallel["strategy"] = "parallel"
    df_parallel = df_parallel[df_parallel["phase"] == "Finished"]
    df_parallel = df_parallel.drop(columns=["phase"])
    df_parallel["whs"] = 1.0

    df = pd.concat([df_jet, df_dendrotime, df_parallel], ignore_index=True)
    df["runtime"] = df["runtime"] / 1000  # convert to seconds
    # df = df[df["dataset"].isin(selected_datasets)]
    # df = df[df["dataset"].isin(LONG_RUNNING_DATASETS)]

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

    distances = set(df["distance"].unique().tolist())
    if not include_lorentzian:
        distances = distances - {"lorentzian"}
    if not include_euclidean:
        distances = distances - {"euclidean"}
    distances = sorted(distances, key=lambda x: distance_order.index(x))
    linkages = set(df["linkage"].unique())
    if not include_ward:
        linkages = linkages - {"ward"}
    linkages = sorted(linkages)

    # use right y ticks and labels for this plot
    plt.rcParams["ytick.right"] = plt.rcParams["ytick.labelright"] = True
    plt.rcParams["ytick.left"] = plt.rcParams["ytick.labelleft"] = False

    fig, axs = plt.subplots(
        len(distances),
        len(linkages),
        figsize=(8, len(distances)),
        sharex="none",
        sharey="none",
        constrained_layout=False,
        gridspec_kw={"hspace": 0.5, "wspace": 0.2},
    )
    # configure labels and headers
    for i, distance in enumerate(distances):
        rowHeaderAx = axs[i, 0].twinx()
        rowHeaderAx.yaxis.set_label_position("left")
        rowHeaderAx.set_yticks([])
        rowHeaderAx.set_yticklabels([])
        rowHeaderAx.set_ylabel(distance, size="large")
        for spine in rowHeaderAx.spines.values():
            spine.set_visible(False)
        axs[i, 0].yaxis.set_label_position("right")
        axs[i, 0].yaxis.set_ticks_position("right")
        axs[i, 0].yaxis.tick_right()

        for j in range(axs.shape[1]):
            # helper grid line:
            axs[i, j].axvline(x=1, color="lightgray", ls="--", lw=1)
            axs[i, j].set_ylim(-0.05, 1.1)
            axs[i, j].set_yticks([0.0, 0.5, 1.0])
            axs[i, j].set_yticklabels([])
            axs[i, j].yaxis.set_label_position("right")

        axs[i, -1].set_ylabel("WHS")
        axs[i, -1].set_yticklabels([0.0, 0.5, 1.0])

    for j, linkage in enumerate(linkages):
        axs[0, j].set_title(linkage, size="large")
        axs[-1, j].set_xlabel("relative runtime")

    # add plots
    handles, labels = [], []
    for i, distance in enumerate(distances):
        # aggregate WHS and runtime over datasets to get a single point for JET
        # JET only supports ward linkage, so broadcast to all linkages
        df_jet = df[
            (df["linkage"] == "ward")
            & (df["distance"] == distance)
            & (df["strategy"] == "JET")
        ]
        df_jet = df_jet[["whs", "runtime"]].agg(["mean", "median", "std"], axis=0)

        for j, linkage in enumerate(linkages):
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

            # get maximum runtime for scaling and extending strategy lines to right
            jet_whs = df_jet.loc["mean", "whs"]
            jet_runtime = df_jet.loc["mean", "runtime"]
            max_other_runtime = df_filtered[("runtime", "mean")].max()
            max_runtime = max(jet_runtime, max_other_runtime)

            # cut axis, where JET runtime is large into two
            break_point = max(max_other_runtime*1.25, 2.1)
            if jet_runtime > break_point:
                # we get the gridspec of the axis, remove the axis, add a subgridspec to
                # it, and then add two new axes to it
                kwargs = dict(wspace=0.05, hspace=0.0)
                gs = ax.get_subplotspec().subgridspec(1, 2, width_ratios=[3, 1], **kwargs)
                ax_default = fig.add_subplot(gs[0, 0])
                ax_jet = fig.add_subplot(gs[0, 1])
                ax.remove()

                # reconfigure the default axis
                ax_default.set_xlim(-0.1, break_point)
                ax_default.spines["right"].set_visible(False)
                ax_default.set_ylim(-0.05, 1.1)
                ax_default.set_yticks([])
                ax_default.set_yticks([], minor=True)
                ax_default.set_yticklabels([])
                ax_default.yaxis.set_ticks_position("none")
                ax_default.tick_params(labelright=False, labelleft=False)
                if j == 0:
                    ax_default.set_ylabel(" ", size="large")

                ax_default.axvline(x=1, color="lightgray", ls="--", lw=1)

                if i == 0:
                    ax_default.set_title(linkage, size="large")
                if i == len(distances) - 1:
                    ax_default.set_xlabel("relative runtime")

                # configure the JET axis
                ax_jet.set_ylim(-0.05, 1.1)
                ax_jet.set_yticks([0.0, 0.5, 1.0])
                ax_jet.yaxis.set_label_position("right")

                if j == len(linkages) - 1:
                    ax_jet.set_ylabel("WHS")
                    ax_jet.set_yticklabels([0.0, 0.5, 1.0])
                    ax_jet.tick_params(labelright=True, labelleft=False)
                else:
                    ax_jet.set_yticklabels([])
                    ax_jet.tick_params(labelright=False, labelleft=False)

                # adjust y-axis limits to show JET point (zooms)
                ax_jet.set_xlim(break_point, 1.1*max_runtime)
                ax_jet.set_xticks([jet_runtime])
                ax_jet.set_xticklabels([f"{jet_runtime:.1f}"])
                ax_jet.spines["left"].set_visible(False)

                # add slanted lines to indicate the break
                d = .75  # proportion of vertical to horizontal extent of the slanted line
                kwargs = dict(marker=[(-1, -d), (1, d)], markersize=6,
                              linestyle="none", color='k', mec='k', mew=1, clip_on=False)
                ax_jet.plot([0, 0], [0, 1], transform=ax_jet.transAxes, **kwargs)
                ax_default.plot([1, 1], [0, 1], transform=ax_default.transAxes, **kwargs)
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
                    label=strategy_name(strategy),
                    marker=marker,
                    zorder=2.5,
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
                if jet_runtime > break_point:
                    # add JET to the legend
                    jet_handles, jet_labels = ax_jet.get_legend_handles_labels()
                    handles.extend(jet_handles)
                    labels.extend(jet_labels)

    # add legend
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
    # fig.savefig(
    #     "mean-runtime-qualities.png", bbox_inches="tight", bbox_extra_artists=[legend]
    # )
    # plt.show()


if __name__ == "__main__":
    args = parse_args()
    main(
        show_jet_variance=args.show_jet_variance,
        include_euclidean=args.include_euclidean,
        include_lorentzian=args.include_lorentzian,
        include_ward=args.include_ward,
        disable_variances=args.disable_variances,
        correct_dendrotime_runtime=args.correct_dendrotime_runtime,
        extend_strategy_runtimes=args.extend_strategy_runtimes,
    )
