#!/usr/bin/env python3

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from scipy.cluster.hierarchy import cut_tree
from aeon.datasets import load_classification
from sklearn.metrics import adjusted_rand_score


def load_quality_trace(strategy, dataset, distance, linkage):
    df = pd.read_csv(
        f"04-dendrotime/results/{dataset}-{distance}-{linkage}-{strategy}/Finished-100/qualities.csv"
    )
    df["strategy"] = strategy
    # use relative runtime instead of millis since epoch
    df["timestamp"] = df["timestamp"] - df.loc[0, "timestamp"]
    # convert millis to seconds
    df["timestamp"] = df["timestamp"] / 1000
    return df


def compute_serial_quality(dataset, distance, linkage):
    try:
        _, y = load_classification(dataset, extract_path="data/datasets", load_equal_length=False)
        n_clusters = len(np.unique(y))
        Z = np.loadtxt(f"01-serial-hac/results/{dataset}-{distance}-{linkage}-approx_distance_ascending/serial/hierarchy.csv", delimiter=",")
        clusters = cut_tree(Z, n_clusters=n_clusters).flatten()
        ari = adjusted_rand_score(y, clusters)
        return ari
    except (FileNotFoundError, ValueError) as e:
        print(f"Failed to compute quality for serial {dataset}-{distance}-{linkage}: {e}")
        return np.nan


def plot_quality_trace(df, dataset, distance, linkage):
    static_strategies = ["serial", "JET"]
    dendrotime_strategies = [
        s for s in df["strategy"].unique().tolist() if s not in static_strategies
    ]

    # locate quality traces for dendrotime
    dfs = []
    for strategy in dendrotime_strategies:
        try:
            dfs.append(load_quality_trace(strategy, dataset, distance, linkage))
        except FileNotFoundError:
            print(f"Quality trace for {strategy} not found")
    df_qualities = pd.concat(dfs, ignore_index=True)
    runtime_unit = "s"

    # extract runtime & quality of static approaches
    df_static = df[df["strategy"].isin(["serial", "JET"])]
    df_static = df_static[(df_static["dataset"] == dataset) & (df_static["distance"] == distance) & (df_static["linkage"] == linkage)]
    df_static = df_static.set_index("strategy")[["runtime", "ARI"]]
    df_static.loc["serial", "ARI"] = compute_serial_quality(dataset, distance, linkage)
    static_strategies = [
        s for s in static_strategies if s in df_static.index.tolist()
    ]
    print(df_static)


    fig, ax = plt.subplots(constrained_layout=True)
    ax.set_title(f"Quality trace for {dataset} ({distance}, {linkage})")

    ax.grid(visible=True, which="major", axis="y", linestyle="dotted", linewidth=1)
    for strategy in static_strategies:
        ax.plot(df_static.loc[strategy, "runtime"], df_static.loc[strategy, "ARI"], "o", label=strategy)
    for strategy in dendrotime_strategies:
        group = df_qualities[df_qualities["strategy"] == strategy]
        ax.step(
            group["timestamp"],
            group["cluster-quality"],
            where="post",
            label=strategy,
            lw=2,
        )
        # ax.fill_between(
        #     group["timestamp"], group["cluster-quality"], alpha=0.2, step="post"
        # )
    ax.set_xlabel(f"Runtime ({runtime_unit})")
    ax.set_ylim(0.0, 1.05)
    ax.set_ylabel("Quality")
    ax.legend()

    return fig


def main():
    distance = "msm"
    linkage = "ward"
    max_datasets = 135

    # load results from serial execution
    df_serial = pd.read_csv("01-serial-hac/results/aggregated-runtimes.csv")
    df_serial["strategy"] = "serial"
    df_serial = df_serial[df_serial["phase"] == "Finished"]
    df_serial = df_serial.drop(columns=["phase"])

    # load results from jet execution
    df_jet = pd.read_csv("06-jet/results/results.csv")
    df_jet["strategy"] = "JET"
    df_jet["distance"] = np.tile(["sbd", "msm", "dtw"], df_jet.shape[0] // 3)
    # df_jet["linkage"] = "ward"
    # overwrite distance and linkage
    # df_jet["distance"] = distance
    df_jet["linkage"] = linkage

    # load results from parallel execution
    df_dendrotime = pd.read_csv("04-dendrotime/results/aggregated-runtimes.csv")
    df_dendrotime = df_dendrotime[df_dendrotime["phase"] == "Finished"]

    df = pd.concat([df_serial, df_jet, df_dendrotime], ignore_index=True)

    # select distance and linkage
    df = df[(df["distance"] == distance) & (df["linkage"] == linkage)]
    df["runtime"] = df["runtime"] / 1000  # convert to seconds

    # datasets
    print(f"Processed datasets per strategy (of {max_datasets}):")
    df_datasets = df.groupby("strategy").size().reset_index(name="counts")
    print(df_datasets)

    # plot
    fig, ax = plt.subplots()
    ax.set_title(f"Runtime comparison for {distance} and {linkage}")
    ax.set_ylabel("Runtime [s]")
    ax.set_yscale("log")
    ax.set_ylim(1e-3, 3600*24)
    ax.set_yticks([1e-3, 1e-1, 1, 60, 3600, 3600*24])
    ax.set_yticklabels(["1ms", "100ms", "1s", "1m", "1h", "1d"])
    ax.yaxis.grid(True, which="major", linestyle=":", lw=1)

    strategies = df["strategy"].unique().tolist()
    for strategy, group in df.groupby("strategy"):
        ax.boxplot(
            group["runtime"],
            positions=[strategies.index(strategy)],
            tick_labels=[strategy],
            widths=0.6,
            whis=(0, 100),
            meanline=True,
            showmeans=True,
            sym="",
        )

    plot_quality_trace(df, "FaceFour", distance, linkage)
    plt.show()


if __name__ == "__main__":
    main()
