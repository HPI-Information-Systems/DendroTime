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


def plot_quality_trace(df, configs, show_ari=False):
    runtime_unit = "s"
    dynamic_strategies = [
        s for s in df["strategy"].unique().tolist() if s not in baseline_strategies
    ][::-1]

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
    df_static = df_static[["runtime", "ARI", "whs"]]
    df_static = df_static.sort_index()

    if show_ari:
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
        2 if show_ari else 1,
        len(configs),
        squeeze=False,
        sharex="col",
        sharey="row",
        constrained_layout=True,
        figsize=(12, 4 if show_ari else 2),
    )

    axs[0, 0].set_ylim(-0.05, 1.05)
    axs[0, 0].set_ylabel(measure_name_mapping["weightedHierarchySimilarity"])
    for i, (dataset, distance, linkage) in enumerate(configs):
        axs[0, i].set_title(f"{dataset_name(dataset)} ({distance}, {linkage})", fontsize=10)
        axs[0, i].grid(
            visible=True, which="major", axis="y", linestyle="dotted", linewidth=1
        )
        max_runtime = np.max(np.r_[
            df_static.loc[(slice(None), dataset, distance, linkage), "runtime"].values,
            df_qualities.loc[(slice(None), dataset, distance, linkage), "timestamp"].values
        ])
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
                np.r_[group["timestamp"], max_runtime],
                np.r_[group["hierarchy-quality"], group["hierarchy-quality"].iloc[-1]],
                where="post",
                color=color,
                lw=2,
                label=strategy_name(strategy),
            )
        for strategy in static_strategies:
            try:
                entry = df_static.loc[(strategy, dataset, distance, linkage)]
            except KeyError:
                print(
                    f"Could not plot static quality for {strategy} - {dataset}-{distance}-{linkage}"
                )
            if strategy == "JET":
                axs[0, i].plot(
                    entry["runtime"],
                    entry["whs"],
                    markersize=8,
                    marker=markers[strategy],
                    color=colors[strategy],
                    label=strategy_name(strategy),
                )
            else:
                axs[0, i].plot(
                    entry["runtime"],
                    1.0,
                    markersize=8,
                    marker=markers[strategy],
                    color=colors[strategy],
                    label=strategy_name(strategy),
                )

        axs[-1, i].set_xlabel(f"Runtime ({runtime_unit})")

    # plot row of ARIs
    if show_ari:
        axs[1, 0].set_ylim(-0.55, 1.05)
        axs[1, 0].set_ylabel(measure_name_mapping["ari"])

        for i, (dataset, distance, linkage) in enumerate(configs):
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

    handles, labels = axs[0, 0].get_legend_handles_labels()
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
    df = df.dropna(subset=["runtime"], axis=0, how="any")

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


def create_runtime_table(df, distance, linkage):
    # select distance and linkage
    # (might be able to remove if we have a small number of datasets)
    df = df[(df["distance"] == distance) & (df["linkage"] == linkage)]
    df = df.drop(columns=["distance", "linkage"])

    # select datasets
    df_ari = df[df["strategy"] == "serial"]
    # datasets = df_ari.loc[
    #     (df_ari["runtime"] >= 5*60),
    #     "dataset"
    # ].unique().tolist()
    datasets = df_ari["dataset"].unique().tolist()
    df_ari = df_ari[df_ari["dataset"].isin(datasets)].set_index("dataset")[["ARI"]]
    df = df[df["dataset"].isin(datasets)]

    # extract JET qualities
    df_jet = df[df["strategy"] == "JET"]
    df_jet = df_jet[df_jet["dataset"].isin(datasets)].set_index("dataset")[["whs"]]
    df_jet.columns = ["JET WHS"]

    # table layout:
    # rows: datasets
    # columns: runtimes of serial, parallel, JET, 3 dendrotime strategies (for hwsim & ari)
    df = df.pivot(index="dataset", columns="strategy", values="runtime")
    df = pd.merge(df, df_ari, left_index=True, right_index=True, how="inner")
    df = pd.merge(df, df_jet, left_index=True, right_index=True, how="inner")

    first_columns = [c for c in baseline_strategies if c in df.columns]
    df = df[first_columns + df.columns.drop(first_columns).tolist()]
    df = df.sort_values("parallel", ascending=True, na_position="last")
    df.columns = [strategy_name(c) for c in df.columns]
    print(f"\nRuntime at hierarchy quality >= 0.8 for distance={distance} and linkage={linkage}:")
    with pd.option_context("display.max_rows", None):
        print(df)

    df = df.dropna(axis=0, how="any")
    df = df[df["parallel"] >= 5*60]
    parallel_runtime = df["parallel"]
    for c in df.columns:
        df[c] = df[c] / parallel_runtime
    index = np.arange(df.shape[0])

    fig, ax = plt.subplots(1, 1, figsize=(12, 6), constrained_layout=True)
    ax.axhline(1, color="black", ls="--", lw=1, label="parallel")
    for i, strategy in enumerate(["JET", "fcfs", "ada", "precl"]):
        ax.bar(index + i/5, df[strategy], width=1/5, align="edge", color=colors[strategy], label=strategy_name(strategy))
    ax.set_xticks(index + 0.5)
    ax.set_xticklabels(df.index, rotation=45, ha="right")
    ax.legend()
    ax.set_ylabel("Relative runtime to parallel strategy")
    ax.set_title(f"Runtime at hierarchy quality >= 0.8 for distance={distance} and linkage={linkage}")
    plt.show()


def plot_jet_whs_qualities(df):
    plt.figure()
    ax = plt.gca()
    df = df[df["linkage"] == "ward"]
    labels = []
    for i, (distance, group) in enumerate(df.groupby("distance")):
        data = group["whs"].values
        data = data[~np.isnan(data)]
        ax.boxplot(
            data,
            positions=[i],
            widths=0.6,
            whis=(0, 100),
            meanline=True,
            showmeans=True,
            sym="",
            label=distance
        )
        labels.append(distance)
    ax.axhline(0.8, color="red", ls="--", lw=2)
    ax.set_xticks([0, 1, 2])
    ax.set_xticklabels(labels)
    ax.set_ylabel("WHS")
    ax.set_ylim(0, 1)
    ax.set_title("JET WHS (Ward linkage)")
    plt.show()


def compare_ada_precl(df, df_data):
    df_data = df_data.set_index("dataset")
    df = df[df["distance"] != "euclidean"]
    # s_parallel = df[df["strategy"] == "parallel"].set_index(["dataset", "distance", "linkage"])["runtime"]
    df = df[df["strategy"].isin(["approx_distance_ascending", "pre_clustering"])]
    df = df.pivot(index=["dataset", "distance", "linkage"], columns="strategy", values="runtime")
    df = df[df["approx_distance_ascending"] > 60]
    # for c in df.columns:
    #     df[c] = (df[c] - s_parallel) / s_parallel
    df["n_instances"] = df.index.get_level_values("dataset").map(df_data["n_instances"])
    df["m_ts_length"] = df.index.get_level_values("dataset").map(df_data["time_series_length"])
    df["n:m ratio"] = df["n_instances"] / df["m_ts_length"]
    df["speedup"] = (df["pre_clustering"] - df["approx_distance_ascending"]) / df["pre_clustering"]
    # df = df.drop(columns=["approx_distance_ascending", "pre_clustering", "n_instances", "m_ts_length"])

    distances = df.index.get_level_values("distance").unique()
    linkages = df.index.get_level_values("linkage").unique()

    print("Ada faster than precl for single linkage?")
    with pd.option_context("display.max_rows", None):
        print(df.loc[(slice(None), slice(None), "single")].groupby(["dataset"])["speedup"].mean())
    # --> single: almost always ada faster than precl (only 4 exceptions for now!)
    print("Ada faster than precl for sbd distance?")
    with pd.option_context("display.max_rows", None):
        print(df.loc[(slice(None), "sbd", slice(None))].groupby(["dataset"])["speedup"].mean())
    # --> sbd distance: precl faster for 7/60 datasets, otherwise ada (~90%)

    df_tmp = df[(df["pre_clustering"] < df["approx_distance_ascending"]) & (df["speedup"] < -0.05)]
    for distance in distances:
        for linkage in linkages:
            precl_faster = df_tmp.loc[(slice(None), distance, linkage)].shape[0]
            ada_faster = df.loc[(slice(None), distance, linkage)].shape[0] - precl_faster
            print(f"precl faster than ada for {distance} - {linkage}: ({precl_faster} vs. {ada_faster})")
            print(df_tmp.loc[(slice(None), distance, linkage)].sort_values("n:m ratio"))
    # --> no real correlation
    return

    # ada and precl vs n
    # fig, axs = plt.subplots(len(distances), len(linkages), figsize=(12, 8), constrained_layout=True)
    # for i, distance in enumerate(distances):
    #     for j, linkage in enumerate(linkages):
    #         ax = axs[i, j]
    #         data = df.loc[(slice(None), distance, linkage)]
    #         ax.scatter(data["n_instances"], data["approx_distance_ascending"], label="ada", color=colors["ada"])
    #         ax.scatter(data["n_instances"], data["pre_clustering"], label="precl", color=colors["precl"])
    #         ax.set_title(f"{distance} - {linkage}")
    #         # ax.set_xscale("log")
    #         # ax.set_yscale("log")
    #         ax.set_xlabel("n")
    #         ax.set_ylabel("runtime")
    #         ax.legend()

    # # ada and precl vs m
    # fig, axs = plt.subplots(len(distances), len(linkages), figsize=(12, 8), constrained_layout=True)
    # for i, distance in enumerate(distances):
    #     for j, linkage in enumerate(linkages):
    #         ax = axs[i, j]
    #         data = df.loc[(slice(None), distance, linkage)]
    #         ax.scatter(data["m_ts_length"], data["approx_distance_ascending"], label="ada", color=colors["ada"])
    #         ax.scatter(data["m_ts_length"], data["pre_clustering"], label="precl", color=colors["precl"])
    #         ax.set_title(f"{distance} - {linkage}")
    #         # ax.set_xscale("log")
    #         # ax.set_yscale("log")
    #         ax.set_xlabel("m")
    #         ax.set_ylabel("runtime")
    #         ax.legend()

    # speedup vs n:m ratio
    fig, axs = plt.subplots(len(distances), len(linkages), figsize=(12, 8), constrained_layout=True)
    for i, distance in enumerate(distances):
        for j, linkage in enumerate(linkages):
            ax = axs[i, j]
            data = df.loc[(slice(None), distance, linkage)]
            ax.scatter(data["n:m ratio"], data["speedup"])
            ax.set_title(f"{distance} - {linkage}")
            # ax.set_xscale("log")
            # ax.set_yscale("log")
            ax.set_xlabel("n:m ratio")
            ax.set_ylabel("speedup")

    # 3d plot
    # df = df[df.notna().all(axis=1)]
    # fig, axs = plt.subplots(len(distances), len(linkages), figsize=(12, 8), constrained_layout=True, subplot_kw={"projection": "3d"})
    # for i, distance in enumerate(distances):
    #     for j, linkage in enumerate(linkages):
    #         ax = axs[i, j]
    #         data = df.loc[(slice(None), distance, linkage)]
    #         ax.scatter(data["n_instances"], data["m_ts_length"], data["speedup"], cmap="inferno")
    #         ax.set_title(f"{distance} - {linkage}")
    #         # ax.set_xscale("log")
    #         # ax.set_yscale("log")
    #         ax.set_xlabel("n")
    #         ax.set_ylabel("m")
    #         ax.set_zlabel("speedup")
    plt.show()


def main():
    df_data = pd.read_csv("data/datasets.csv")
    max_datasets = df_data.shape[0]

    # load results from serial execution
    df_serial = pd.read_csv("01-serial-hac/results/aggregated-runtimes.csv")
    df_serial["strategy"] = "serial"
    df_serial = df_serial[df_serial["phase"] == "Finished"]
    df_serial = df_serial.drop(columns=["phase"])

    # load results from jet execution
    df_jet = pd.read_csv("06-jet/results/results.csv")
    df_jet["strategy"] = "JET"
    df_jet["distance"] = np.tile(["sbd", "msm", "dtw"], df_jet.shape[0] // 3)
    df_jet.replace(-1, np.nan, inplace=True)
    dfs = []
    for l in ["single", "complete", "average", "ward"]:
        df = df_jet.copy()
        df["linkage"] = l
        dfs.append(df)
    df_jet = pd.concat(dfs, ignore_index=True)

    # load results from system execution
    df_dendrotime = pd.read_csv("04-dendrotime/results/aggregated-runtimes.csv")
    df_dendrotime["runtime"] = df_dendrotime["runtime_0.8"]
    df_dendrotime = df_dendrotime.drop(columns=[
        "runtime_0.8", "initializing", "approximating", "computingfulldistances", "finalizing", "finished"
    ])

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
    with pd.option_context("display.max_rows", None):
        print(df_datasets)

    # plot JET WHS qualities
    # plot_jet_whs_qualities(df_jet)

    # create runtime comparison table
    create_runtime_table(df, distance="msm", linkage="average")
    create_runtime_table(df, distance="dtw", linkage="complete")
    create_runtime_table(df, distance="sbd", linkage="complete")

    # plot runtimes
    # plot_runtimes(df, distance="msm", linkage="average")

    # analyze ada precl performance difference
    # compare_ada_precl(df, df_data)

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
    plt.show()


if __name__ == "__main__":
    main()
