#!/usr/bin/env python3
import argparse
import sys

import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))
from plt_commons import measure_name_mapping, colors, extract_measures_from_config


def parse_args(args):
    parser = argparse.ArgumentParser(
        description="Plot the convergence of DendroTime for the EDEN ISS case study."
    )
    parser.add_argument(
        "--use-runtime",
        action="store_true",
        help="Use runtime instead of computational steps as x-axis.",
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
    use_runtime = args.use_runtime
    correct_runtime = args.correct_dendrotime_runtime

    dataset = "edeniss20182020_ics_anomalies_1min"
    strategy = "approx_distance_ascending"
    configs = [("msm", "centroid"), ("dtw", "centroid")]
    plot_results(dataset, strategy, configs, use_runtime, correct_runtime)


def plot_results(dataset, strategy, configs, use_runtime=False, correct_runtime=False):
    fig, axs = plt.subplots(
        1,
        len(configs),
        figsize=(8, 2.5),
        sharey="all",
        sharex="all",
        constrained_layout=True,
    )

    for i, (distance, linkage) in enumerate(configs):
        ax = axs[i]

        ax.set_title(f"{distance.upper()}-{linkage}")
        ax.grid(True, which="major", axis="y", ls=":", lw=1)

        add_plot(
            dataset, distance, linkage, strategy, use_runtime, correct_runtime, ax=ax
        )

        if use_runtime:
            ax.set_xlabel("Runtime (s)")
        else:
            ax.set_xlabel("Computational steps")

    axs[0].set_ylabel("Quality")
    axs[0].set_ylim(0.0, 1.05)
    axs[-1].legend(loc="lower right", bbox_to_anchor=(1.0, 0.0), ncol=1)
    fig.savefig("edeniss-convergence.pdf", bbox_inches="tight")
    # fig.savefig(
    #     "edeniss-convergence.png",
    #     bbox_inches="tight",
    # )
    # plt.show()


def add_plot(
    dataset,
    distance,
    linkage,
    strategy,
    use_runtime=False,
    correct_runtime=False,
    ax=None,
):
    if ax is None:
        ax = plt.gca()

    # load results
    results_path = Path(
        f"{dataset}-{distance}-{linkage}-{strategy.replace('-', '_')}/Finished-100/qualities.csv"
    )
    results_file_quality = (Path("results-quality") / results_path).resolve()
    results_file_no_quality = (Path("results-no-quality") / results_path).resolve()
    if not results_file_quality.exists():
        raise FileNotFoundError(f"Result file {results_file_quality} not found!")
    if correct_runtime and not results_file_no_quality.exists():
        raise FileNotFoundError(f"Result file {results_file_no_quality} not found!")

    # measure details
    config_file = results_file_quality.parent / "config.json"
    measures = extract_measures_from_config(config_file)

    print(
        f"Processing dataset '{dataset}' with distance '{distance}', linkage '{linkage}', and strategy '{strategy}'"
    )

    df = pd.read_csv(results_file_quality)
    # use relative runtime instead of millis since epoch
    df["timestamp"] = df["timestamp"] - df.loc[0, "timestamp"]
    # convert millis to seconds
    df["timestamp"] = df["timestamp"] / 1000

    if correct_runtime:
        # --- runtime correction (remove quality measurement overhead)
        full_runtime = pd.read_csv(
            results_file_quality.parent / "runtimes.csv", index_col=0
        ).loc["Finished", "runtime"]
        no_quality_runtime = pd.read_csv(
            results_file_no_quality.parent / "runtimes.csv", index_col=0
        ).loc["Finished", "runtime"]
        runtime_correction_factor = full_runtime / no_quality_runtime
        df["timestamp"] = df["timestamp"] / runtime_correction_factor
        # --- end runtime correction

    df["index"] = df["index"].astype(int)
    n = df.shape[0]
    middle = df[df["index"] >= n // 2].index[0]
    print("Phase change", middle)
    if use_runtime:
        df = df.set_index("timestamp").drop(columns=["index"])
    else:
        df = df.set_index("index").drop(columns=["timestamp"])

    if "cluster-quality" in df.columns:
        df = df.drop(columns=["cluster-quality"])

    aucs = df.sum(axis=0) / df.shape[0]
    print("AUCs")
    print(aucs)

    # phase change: approximating -> exact
    ax.axvline(
        x=df.index[middle],
        color="gray",
        linestyle="--",
        label="Approx. $\\rightarrow$ Exact",
    )

    # WHS
    if "hierarchy-quality" in df.columns:
        ax.step(
            df.index,
            df["hierarchy-quality"],
            where="post",
            label=measure_name_mapping[measures["hierarchy-quality"]],
            color=colors["hierarchy-quality"],
            lw=2,
        )
        ax.fill_between(
            df.index,
            df["hierarchy-quality"],
            alpha=0.2,
            step="post",
            color=colors["hierarchy-quality"],
        )
        df = df.drop(columns=["hierarchy-quality"])

    # other measures
    for measurement in df.columns:
        ax.plot(
            df.index,
            df[measurement],
            label=measure_name_mapping[measures[measurement]],
            lw=2,
            color=colors[measurement],
        )


if __name__ == "__main__":
    main(sys.argv[1:])
