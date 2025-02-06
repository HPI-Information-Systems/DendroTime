#!/usr/bin/env python3

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from scipy.cluster.hierarchy import cut_tree
from aeon.datasets import load_classification
from sklearn.metrics import adjusted_rand_score
from collections import defaultdict


def strategy_name(name):
    if name == "shortestTs":
        return "ts-length-ascending"
    if name == "fcfs":
        return "fcfs"
    if name in ("approxAscending", "approx_distance_ascending"):
        return "approx-dissimilarity-ascending"
    if name in ("preClustering", "pre_clustering"):
        return "pre-clustering"
    return name


colors = defaultdict(lambda: "blue")
colors["ts-length-ascending"] = "gray"
colors["fcfs"] = "gray"
colors["approx-dissimilarity-ascending"] = "red"
colors["pre-clustering"] = "purple"
colors["serial"] = "black"
colors["parallel"] = "green"
colors["JET"] = "blue"

markers = defaultdict(lambda: "")
markers["serial"] = "o"
markers["parallel"] = "s"
markers["JET"] = "D"


def load_quality_trace(strategy, dataset, distance, linkage):
    df = pd.read_csv(
        f"04-dendrotime/results/{dataset}-{distance}-{linkage}-{strategy}/Finished-100/qualities.csv"
    )
    df["strategy"] = strategy
    df["dataset"] = dataset
    df["distance"] = distance
    df["linkage"] = linkage
    # use relative runtime instead of millis since epoch
    df["timestamp"] = df["timestamp"] - df.loc[0, "timestamp"]
    # convert millis to seconds
    df["timestamp"] = df["timestamp"] / 1000
    return df


def compute_serial_quality(dataset, distance, linkage):
    try:
        _, y = load_classification(
            dataset, extract_path="data/datasets", load_equal_length=False
        )
        n_clusters = len(np.unique(y))
        Z = np.loadtxt(
            f"01-serial-hac/results/{dataset}-{distance}-{linkage}-approx_distance_ascending/serial/hierarchy.csv",
            delimiter=",",
        )
        clusters = cut_tree(Z, n_clusters=n_clusters).flatten()
        ari = adjusted_rand_score(y, clusters)
        return ari
    except (FileNotFoundError, ValueError) as e:
        print(
            f"Failed to compute quality for serial {dataset}-{distance}-{linkage}: {e}"
        )
        return np.nan


def plot_quality_trace(df, configs):
    runtime_unit = "s"
    static_strategies = ["serial", "parallel", "JET"]
    dendrotime_strategies = [
        s for s in df["strategy"].unique().tolist() if s not in static_strategies
    ]

    # locate quality traces for dendrotime
    dfs = []
    for strategy in dendrotime_strategies:
        for dataset, distance, linkage in configs:
            try:
                dfs.append(load_quality_trace(strategy, dataset, distance, linkage))
            except FileNotFoundError:
                print(
                    f"Quality trace for {strategy} - {dataset}-{distance}-{linkage} not found"
                )
    df_qualities = pd.concat(dfs, ignore_index=True)
    df_qualities = df_qualities.set_index(
        ["strategy", "dataset", "distance", "linkage"]
    ).sort_index()

    # extract runtime & quality of static approaches
    df_static = df[df["strategy"].isin(static_strategies)]
    checker = {
        "dataset": [d for d, _, _ in configs],
        "distance": [d for _, d, _ in configs],
        "linkage": [l for _, _, l in configs],
    }
    df_static = df_static[df_static[["dataset", "distance", "linkage"]].isin(checker).all(axis=1)]
    df_static = df_static.set_index(["strategy", "dataset", "distance", "linkage"])
    df_static = df_static[["runtime", "ARI"]]
    df_static = df_static.sort_index()
    print(df_static)
    for dataset, distance, linkage in configs:
        df_static.loc[("serial", dataset, distance, linkage), "ARI"] = (
            compute_serial_quality(dataset, distance, linkage)
        )
    static_strategies = [
        s
        for s in static_strategies
        if s in df_static.index.get_level_values("strategy").unique()
    ]

    fig, axs = plt.subplots(
        2,
        len(configs),
        sharex="col",
        sharey="row",
        constrained_layout=True,
        figsize=(12, 4),
    )
    axs[0, 0].set_ylim(-0.05, 1.05)
    axs[0, 0].set_ylabel("Hierarchy quality (whsim)")
    axs[1, 0].set_ylim(-0.55, 1.05)
    axs[1, 0].set_ylabel("Cluster quality (ARI)")
    for i, (dataset, distance, linkage) in enumerate(configs):
        axs[0, i].set_title(f"{dataset} ({distance}, {linkage})")
        axs[0, i].grid(
            visible=True, which="major", axis="y", linestyle="dotted", linewidth=1
        )
        for strategy in dendrotime_strategies:
            try:
                group = df_qualities.loc[(strategy, dataset, distance, linkage)]
            except KeyError:
                print(
                    f"Could not plot hierarchy quality for {strategy} - {dataset}-{distance}-{linkage}"
                )
                continue
            color = colors[strategy_name(strategy)]
            axs[0, i].step(
                group["timestamp"],
                group["hierarchy-quality"],
                where="post",
                color=color,
                lw=2,
                label=strategy_name(strategy),
            )

        axs[1, i].grid(
            visible=True, which="major", axis="y", linestyle="dotted", linewidth=1
        )
        for strategy in dendrotime_strategies:
            try:
                group = df_qualities.loc[(strategy, dataset, distance, linkage)]
            except KeyError:
                print(
                    f"Could not plot cluster quality for {strategy} - {dataset}-{distance}-{linkage}"
                )
                continue
            color = colors[strategy_name(strategy)]
            axs[1, i].step(
                group["timestamp"],
                group["cluster-quality"],
                where="post",
                color=color,
                lw=2,
                label=strategy_name(strategy),
            )
            # ax.fill_between(
            #     group["timestamp"], group["cluster-quality"], alpha=0.2, step="post"
            # )
        for strategy in static_strategies:
            try:
                entry = df_static.loc[(strategy, dataset, distance, linkage)]
            except KeyError:
                print(
                    f"Could not plot static quality for {strategy} - {dataset}-{distance}-{linkage}"
                )
            axs[1, i].plot(
                entry["runtime"],
                entry["ARI"],
                marker=markers[strategy_name(strategy)],
                color=colors[strategy_name(strategy)],
                label=strategy_name(strategy),
            )
        axs[1, i].set_xlabel(f"Runtime ({runtime_unit})")
    handles, labels = axs[1, 0].get_legend_handles_labels()
    fig.legend(
        handles,
        labels,
        loc="upper center",
        ncol=len(handles) // 2,
        bbox_to_anchor=(0.5, 0.5),
    )
    # fig.savefig("", bbox_inches="tight")
    return fig


def main():
    distance = "msm"
    linkage = "average"
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
    dfs = []
    for l in ["single", "complete", "average", "ward"]:
        df = df_jet.copy()
        df["linkage"] = l
        dfs.append(df)
    df_jet = pd.concat(dfs, ignore_index=True)

    # load results from parallel execution
    df_dendrotime = pd.read_csv("04-dendrotime/results/aggregated-runtimes.csv")
    df_dendrotime = df_dendrotime[df_dendrotime["phase"] == "Finished"]

    print(df_dendrotime.sort_values("runtime", ascending=False)["dataset"].unique())

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
    ax.set_ylim(1e-3, 3600 * 24)
    ax.set_yticks([1e-3, 1e-1, 1, 60, 3600, 3600 * 24])
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

    # FaceAll (JET surprisingly good??)
    # FacesUCR (JET surprisingly good??)
    # MoteStrain (JET surprisingly good??)

    # InsectWingbeatSound (JET is faster, bad convergence)

    # PLAID (bad ARI for all)
    # ItalyPowerDemand (bad ARI for all)

    # edeniss20182020_temp_anomalies (no ground truth)
    # edeniss20182020_ics_anomalies (no ground truth)

    # Haptics (faster, but just (sub-)linear convergence)

    plot_quality_trace(
        df,
        [
            ("ACSF1", distance, "ward"),
            ("PLAID", distance, linkage),
            ("Haptics", distance, linkage),
            ("FaceFour", distance, linkage),
            ("FaceFour", "dtw", linkage),
            ("FaceFour", "sbd", linkage),
        ],
    )
    plt.show()


if __name__ == "__main__":
    main()
