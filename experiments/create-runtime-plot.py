import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

sys.path.append(str(Path(__file__).parent.parent))
from plt_commons import colors, markers, strategy_name


def main(show_jet_variance=False, include_euclidean=False):
    # load results from serial execution
    # df_serial = pd.read_csv("01-serial-hac/results/aggregated-runtimes.csv")
    # df_serial["strategy"] = "serial"
    # df_serial = df_serial[df_serial["phase"] == "Finished"]
    # df_serial = df_serial.drop(columns=["phase"])
    # df_serial["whs"] = 1.0

    # load results from jet execution
    df_jet = pd.read_csv("06-jet/results/results.csv")
    df_jet["strategy"] = "JET"
    df_jet["distance"] = np.tile(["sbd", "msm", "dtw"], df_jet.shape[0] // 3)
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
    df_dendrotime = df_dendrotime.melt(
        id_vars=["dataset", "distance", "linkage", "strategy"],
        var_name="whs",
        value_vars=[c for c in df_dendrotime.columns if c.startswith("runtime")],
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
    # convert runtime to relative to parallel runtimes
    for _, group in df.groupby(["dataset", "distance", "linkage"]):
        try:
            parallel_runtime = group.loc[
                group["strategy"] == "parallel", "runtime"
            ].item()
        except ValueError:
            parallel_runtime = np.nan
        df.loc[group.index, "runtime"] = group["runtime"] / parallel_runtime

    distances = sorted(df["distance"].unique().tolist())
    if not include_euclidean:
        distances = sorted(set(distances) - {"euclidean"})
    linkages = sorted(df["linkage"].unique().tolist())

    # use right y ticks and labels for this plot
    plt.rcParams["ytick.right"] = plt.rcParams["ytick.labelright"] = True
    plt.rcParams["ytick.left"] = plt.rcParams["ytick.labelleft"] = False

    fig, axs = plt.subplots(
        len(distances),
        len(linkages),
        figsize=(8, 3),
        sharex="col",
        sharey="none",
        constrained_layout=True,
    )
    # configure labels and headers
    for i, distance in enumerate(distances):
        rowHeaderAx = axs[i, 0].twinx()
        rowHeaderAx.yaxis.set_label_position("left")
        rowHeaderAx.spines["left"].set_visible(False)
        rowHeaderAx.set_yticks([])
        rowHeaderAx.set_yticklabels([])
        rowHeaderAx.set_ylabel(distance, size="large")
        axs[i, 0].yaxis.set_label_position("right")
        axs[i, 0].yaxis.set_ticks_position("right")
        axs[i, 0].yaxis.tick_right()

        for j in range(axs.shape[1]):
            # helper grid line:
            axs[i, j].axvline(x=1, color="lightgray", ls="--", lw=1)
            axs[i, j].set_ylim(-0.05, 1.05)
            axs[i, j].set_yticks([0.0, 0.5, 1.0])
            axs[i, j].set_yticklabels([])
            axs[i, j].yaxis.set_label_position("right")

        axs[i, -1].set_ylabel("WHS")
        axs[i, -1].set_yticklabels([0.0, 0.5, 1.0])

    for j, linkage in enumerate(linkages):
        axs[0, j].set_title(linkage, size="large")
        axs[-1, j].set_xlabel("relative runtime")

    # add plots
    for i, distance in enumerate(distances):
        for j, linkage in enumerate(linkages):
            ax = axs[i, j]

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

            # aggregate WHS and runtime over datasets to get a single point for JET
            df_jet = df[
                (df["linkage"] == linkage)
                & (df["distance"] == distance)
                & (df["strategy"] == "JET")
            ]
            df_jet = df_jet[["whs", "runtime"]].agg(["mean", "std"], axis=0)

            # get maximum runtime for scaling and extending strategy lines to right
            max_runtime = max(
                df_filtered[("runtime", "mean")].max(),
                df_jet.loc["mean", "runtime"].max(),
            )

            # add plots for all strategies
            for strategy in ["fcfs", "pre_clustering", "approx_distance_ascending"]:
                color = colors[strategy]
                runtimes = np.r_[
                    df_filtered.loc[strategy, ("runtime", "mean")], [max_runtime]
                ]
                stddevs = np.r_[df_filtered.loc[strategy, ("runtime", "std")], [0.0]]
                whss = np.r_[df_filtered.loc[strategy, "whs"], [1.0]]
                ax.plot(runtimes, whss, label=strategy_name(strategy), color=color)
                ax.fill_betweenx(
                    whss, runtimes - stddevs, runtimes + stddevs, color=color, alpha=0.1
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
            ax.plot(
                df_filtered.loc[strategy, ("runtime", "mean")],
                df_filtered.loc[strategy, "whs"],
                label=strategy_name(strategy),
                color=color,
                marker=markers[strategy],
            )

            # add plot for JET
            strategy = "JET"
            color = colors[strategy]
            if show_jet_variance:
                ax.errorbar(
                    df_jet.loc["mean", "runtime"],
                    df_jet.loc["mean", "whs"],
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
                ax.plot(
                    df_jet.loc["mean", "runtime"],
                    df_jet.loc["mean", "whs"],
                    label=strategy_name(strategy),
                    color=color,
                    marker=markers[strategy],
                )

    # add legend
    handles, labels = axs[-1, -1].get_legend_handles_labels()
    legend = fig.legend(
        handles,
        labels,
        loc="upper center",
        ncol=len(handles),
        bbox_to_anchor=(0.5, -0.05),
    )
    fig.savefig(
        "mean-runtime-qualities.pdf", bbox_inches="tight", bbox_extra_artists=[legend]
    )
    fig.savefig(
        "mean-runtime-qualities.png", bbox_inches="tight", bbox_extra_artists=[legend]
    )
    plt.show()


if __name__ == "__main__":
    main()
