#!/usr/bin/env python3
import numpy as np
import pandas as pd

import matplotlib.pyplot as plt

from scipy.cluster.hierarchy import cut_tree
from aeon.datasets import load_classification
from sklearn.metrics import adjusted_rand_score
from plt_commons import (
    colors,
    markers,
    strategy_name,
    dataset_name,
    baseline_strategies,
    dendrotime_strategies,
    measure_name_mapping
)


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
    dynamic_strategies = [
        s for s in df["strategy"].unique().tolist() if s not in baseline_strategies
    ]

    # locate quality traces for dendrotime
    dfs = []
    for strategy in dynamic_strategies:
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
    df_static = df[df["strategy"].isin(baseline_strategies)]
    checker = {
        "dataset": [d for d, _, _ in configs],
        "distance": [d for _, d, _ in configs],
        "linkage": [l for _, _, l in configs],
    }
    df_static = df_static[
        df_static[["dataset", "distance", "linkage"]].isin(checker).all(axis=1)
    ]
    df_static = df_static.set_index(["strategy", "dataset", "distance", "linkage"])
    df_static = df_static[["runtime", "ARI"]]
    df_static = df_static.sort_index()

    for dataset, distance, linkage in configs:
        if pd.isna(df_static.loc[("serial", dataset, distance, linkage), "ARI"]):
            df_static.loc[("serial", dataset, distance, linkage), "ARI"] = (
                compute_serial_quality(dataset, distance, linkage)
            )
    static_strategies = [
        s
        for s in baseline_strategies
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
    axs[0, 0].set_ylabel(measure_name_mapping["weightedHierarchySimilarity"])
    axs[1, 0].set_ylim(-0.55, 1.05)
    axs[1, 0].set_ylabel(measure_name_mapping["ari"])
    for i, (dataset, distance, linkage) in enumerate(configs):
        axs[0, i].set_title(f"{dataset_name(dataset)} ({distance}, {linkage})", fontsize=10)
        axs[0, i].grid(
            visible=True, which="major", axis="y", linestyle="dotted", linewidth=1
        )
        for strategy in dynamic_strategies:
            try:
                group = df_qualities.loc[(strategy, dataset, distance, linkage)]
            except KeyError:
                print(
                    f"Could not plot hierarchy quality for {strategy} - {dataset}-{distance}-{linkage}"
                )
                continue
            color = colors[strategy]
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
        for strategy in dynamic_strategies:
            try:
                group = df_qualities.loc[(strategy, dataset, distance, linkage)]
            except KeyError:
                print(
                    f"Could not plot cluster quality for {strategy} - {dataset}-{distance}-{linkage}"
                )
                continue
            color = colors[strategy]
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
                markersize=8,
                marker=markers[strategy],
                color=colors[strategy],
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
    fig.savefig("example-runtime-quality-traces.pdf", bbox_inches="tight")
    return fig


def plot_runtimes(df, distance, linkage):
    # select distance and linkage
    df = df[(df["distance"] == distance) & (df["linkage"] == linkage)]

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
            tick_labels=[strategy_name(strategy)],
            widths=0.6,
            whis=(0, 100),
            meanline=True,
            showmeans=True,
            sym="",
        )
    return fig


def runtime_at_quality(strategy, dataset, distance, linkage, threshold, measure):
    try:
        df = load_quality_trace(strategy, dataset, distance, linkage)
        # use relative runtime instead of millis since epoch
        df["timestamp"] = df["timestamp"] - df.loc[0, "timestamp"]
        # convert millis to seconds
        df["timestamp"] = df["timestamp"] / 1000
        df["index"] = df["index"].astype(int)

        return df.loc[df[measure] >= threshold, "timestamp"].iloc[0]
    except FileNotFoundError:
        print(
            f"Quality trace for {strategy} - {dataset}-{distance}-{linkage} not found"
        )
        return np.nan


def create_runtime_table(df, distance, linkage, threshold):
    # select distance and linkage
    # (might be able to remove if we have a small number of datasets)
    df = df[(df["distance"] == distance) & (df["linkage"] == linkage)]
    df = df.drop(columns=["distance", "linkage", "phase"])

    # select datasets
    df_ari = df[df["strategy"] == "serial"]
    datasets = df_ari.loc[
        (df_ari["ARI"] >= 0.2) & (df_ari["runtime"] >= 30),
        "dataset"
    ].unique().tolist()
    df_ari = df_ari[df_ari["dataset"].isin(datasets)].set_index("dataset")[["ARI"]]
    df = df[df["dataset"].isin(datasets)]

    # compute runtime at quality threshold for dendrotime strategies
    dt_mask = df["strategy"].isin(dendrotime_strategies)
    df.loc[dt_mask, "runtime"] = df.loc[dt_mask, ["dataset", "strategy"]].apply(
        lambda x: runtime_at_quality(
            x["strategy"],
            x["dataset"],
            distance,
            linkage,
            threshold,
            "hierarchy-quality",
        ),
        axis=1,
    )

    # table layout:
    # rows: datasets
    # columns: runtimes of serial, parallel, JET, 3 dendrotime strategies (for hwsim & ari)
    df = df.pivot(index="dataset", columns="strategy", values="runtime")
    df = pd.merge(df, df_ari, left_index=True, right_index=True, how="inner")

    first_columns = [c for c in baseline_strategies if c in df.columns]
    df = df[first_columns + df.columns.drop(first_columns).tolist()]
    df = df.sort_values("approx_distance_ascending", ascending=True, na_position="last")
    df.columns = [strategy_name(c) for c in df.columns]
    print(f"\nRuntime at hierarchy quality >= {threshold:.2f} for distance={distance} and linkage={linkage}:")
    with pd.option_context("display.max_rows", None):
        print(df)


def main():
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
    dfs = []
    for l in ["single", "complete", "average", "ward"]:
        df = df_jet.copy()
        df["linkage"] = l
        dfs.append(df)
    df_jet = pd.concat(dfs, ignore_index=True)

    # load results from system execution
    df_dendrotime = pd.read_csv("04-dendrotime/results/aggregated-runtimes.csv")
    df_dendrotime = df_dendrotime[df_dendrotime["phase"] == "Finished"]

    # load results from parallel execution
    df_parallel = pd.read_csv("07-parallel-hac/results/aggregated-runtimes.csv")
    df_parallel["strategy"] = "parallel"
    df_parallel = df_parallel[df_parallel["phase"] == "Finished"]
    df_parallel = df_parallel.drop(columns=["phase"])

    df = pd.concat([df_serial, df_jet, df_dendrotime, df_parallel], ignore_index=True)
    df["runtime"] = df["runtime"] / 1000  # convert to seconds

    # datasets
    print(f"Processed datasets per strategy (of {max_datasets}):")
    df_datasets = (
        df.groupby(["strategy", "distance", "linkage"])
        .size()
        .reset_index(name="counts")
    )
    print(df_datasets)

    # create runtime comparison table
    create_runtime_table(df, distance="msm", linkage="average", threshold=0.8)
    create_runtime_table(df, distance="dtw", linkage="complete", threshold=0.8)
    create_runtime_table(df, distance="sbd", linkage="complete", threshold=0.8)

    # plot runtimes
    # plot_runtimes(df, distance="msm", linkage="average")

    # create example runtime-quality traces
    # FaceAll (JET surprisingly good??)
    # FacesUCR (JET surprisingly good??)
    # MoteStrain (JET surprisingly good??)

    # InsectWingbeatSound (JET is faster, bad convergence)

    # PLAID (bad ARI for all)
    # ItalyPowerDemand (bad ARI for all)

    # edeniss20182020_temp_anomalies (no ground truth)
    # edeniss20182020_ics_anomalies (no ground truth)

    # Haptics (faster, but just (sub-)linear convergence)

    # plot_quality_trace(
    #     df,
    #     [
    #         ("ACSF1", "msm", "ward"),
    #         ("PLAID", "msm", "average"),
    #         # ("Haptics", "msm", "average"),
    #         ("FaceFour", "msm", "average"),
    #         ("FaceFour", "dtw", "average"),
    #         ("FaceFour", "sbd", "average"),
    #         ("HandOutlines", "msm", "average"),
    #     ],
    # )
    # plt.show()


if __name__ == "__main__":
    main()
