import sys

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))
from plt_commons import strategy_name, colors, markers


def main():
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
    df_jet["linkage"] = "ward"
    # dfs = []
    # for l in ["single", "complete", "average", "ward"]:
    #     df = df_jet.copy()
    #     df["linkage"] = l
    #     dfs.append(df)
    # df_jet = pd.concat(dfs, ignore_index=True)

    # load results from system execution
    df_dendrotime = pd.read_csv("04-dendrotime/results/aggregated-runtimes.csv")
    df_dendrotime["runtime_1.0"] = df_dendrotime["finished"]
    df_dendrotime = df_dendrotime.drop(columns=[
        "initializing", "approximating", "computingfulldistances", "finalizing", "finished"
    ])
    df_dendrotime = df_dendrotime.melt(
        id_vars=["dataset", "distance", "linkage", "strategy"],
        var_name="whs",
        value_vars=[c for c in df_dendrotime.columns if c.startswith("runtime")],
        value_name="runtime",
        ignore_index=True
    )
    df_dendrotime["whs"] = df_dendrotime["whs"].str.replace("runtime_", "").astype(float)

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
            parallel_runtime = group.loc[group["strategy"] == "parallel", "runtime"].item()
        except ValueError:
            parallel_runtime = np.nan
        df.loc[group.index, "runtime"] = group["runtime"] / parallel_runtime

    distances = df["distance"].unique().tolist()
    distances = sorted(distances)
    # distances = sorted(set(distances) - {"euclidean"})
    linkages = sorted(df["linkage"].unique().tolist())

    fig, axs = plt.subplots(len(linkages), len(distances), figsize=(12, 6), sharex="none", sharey="all", constrained_layout=True)
    for i, linkage in enumerate(linkages):
        for j, distance in enumerate(distances):
            ax = axs[i, j]
            ax.axvline(x=1, color="lightgray", linestyle="--", label="parallel runtime")
            ax.set_ylim(-0.05, 1.05)
            if i == 0:
                ax.set_title(distance)

            df_filtered = df[(df["linkage"] == linkage) & (df["distance"] == distance) & (df["strategy"] != "JET")]
            df_filtered = df_filtered.groupby(["strategy", "whs"])[["runtime"]].mean().reset_index().set_index("strategy")
            df_jet = df[(df["linkage"] == linkage) & (df["distance"] == distance) & (df["strategy"] == "JET")]
            df_jet = df_jet[["whs", "runtime"]].mean()
            max_runtime = max(df_filtered["runtime"].max(), df_jet["runtime"].max())

            for strategy in ["approx_distance_ascending", "pre_clustering", "fcfs"]:
                color = colors[strategy]
                # runtimes = df_filtered.loc[strategy, "runtime"]
                # whss = df_filtered.loc[strategy, "whs"]
                runtimes = np.r_[df_filtered.loc[strategy, "runtime"], [max_runtime]]
                whss = np.r_[df_filtered.loc[strategy, "whs"], [1.0]]
                ax.plot(runtimes, whss, label=strategy_name(strategy), color=color)

            for strategy in ["parallel"]:
                color = colors[strategy]
                ax.plot(df_filtered.loc[strategy, "runtime"], df_filtered.loc[strategy, "whs"], label=strategy_name(strategy), color=color, marker=markers[strategy])

            # add plot for JET (aggregate WHS and runtime over datasets to get a single point)
            strategy = "JET"
            color = colors[strategy]
            ax.plot(df_jet["runtime"], df_jet["whs"], label=strategy_name(strategy), color=color, marker=markers[strategy])

        axs[i, 0].set_ylabel(linkage)

    for j in range(len(distances)):
        axs[-1, j].set_xlabel("Runtime (relative to parallel)")

    handles, labels = axs[-1, -1].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper center", ncol=len(handles), bbox_to_anchor=(0.5, -1.1))
    plt.show()


if __name__ == "__main__":
    main()
