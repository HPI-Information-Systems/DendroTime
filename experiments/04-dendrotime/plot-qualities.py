#!/usr/bin/env python3
import argparse
import json
import sys

import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))
from plt_commons import measure_name_mapping, colors, extract_measures_from_config


def parse_args(args):
    parser = argparse.ArgumentParser(
        description="Analyze a DendroTime experiment and plot the qualities."
        "Either provide the result file or the dataset, distance, linkage, and strategy names."
    )
    parser.add_argument(
        "--resultfile", type=str, help="The qualities CSV file to analyze."
    )
    parser.add_argument("--dataset", type=str, help="Dataset name")
    parser.add_argument(
        "--distance",
        type=str,
        default="msm",
        choices=["dtw", "msm", "sbd", "euclidean"],
        help="Distance measure",
    )
    parser.add_argument(
        "--linkage",
        type=str,
        default="average",
        choices=["single", "complete", "average", "weighted"],
        help="Linkage method",
    )
    parser.add_argument(
        "--strategy",
        type=str,
        default="approx-distance-ascending",
        choices=["pre-clustering", "approx-distance-ascending", "fcfs"],
        help="Strategy name",
    )
    parser.add_argument(
        "--use-runtime",
        action="store_true",
        help="Use runtime instead of computational steps as x-axis.",
    )
    parser.add_argument(
        "--include-ari",
        action="store_true",
        help="Include ARI in the plot.",
    )

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    use_runtime = args.use_runtime
    include_ari = args.include_ari
    if args.resultfile is not None:
        results_file = Path(args.resultfile)
    elif args.dataset is None:
        raise ValueError("Either provide the result file or the dataset name.")
    else:
        dataset = args.dataset
        distance = args.distance
        linkage = args.linkage
        strategy = args.strategy
        results_file = Path(
            f"results/{dataset}-{distance}-{linkage}-{strategy.replace('-', '_')}/Finished-100/qualities.csv"
        )

    results_file = results_file.resolve()
    if not results_file.exists():
        raise FileNotFoundError(f"Result file {results_file} not found!")

    plot_results(results_file, use_runtime, include_ari)


def plot_results(results_file, use_runtime=False, include_ari=False):
    # experiment details
    parts = results_file.parent.parent.stem.split("-")
    dataset = parts[0]
    distance = parts[1]
    linkage = parts[2]
    strategy = parts[3]

    # measure details
    config_file = results_file.parent / "config.json"
    measures = extract_measures_from_config(config_file)

    print(
        f"Processing dataset '{dataset}' with distance '{distance}', linkage '{linkage}', and strategy '{strategy}'"
    )

    figures_dir = Path("figures")
    figures_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(results_file)
    # use relative runtime instead of millis since epoch
    df["timestamp"] = df["timestamp"] - df.loc[0, "timestamp"]
    runtime_unit = "ms"
    if df["timestamp"].max() > 2000:
        # convert millis to seconds
        df["timestamp"] = df["timestamp"] / 1000
        runtime_unit = "s"

    df["index"] = df["index"].astype(int)
    n = df.shape[0]
    middle = df[df["index"] >= n // 2].index[0]
    print("Phase change", middle)
    if use_runtime:
        df = df.set_index("timestamp").drop(columns=["index"])
    else:
        df = df.set_index("index").drop(columns=["timestamp"])

    if not include_ari and "cluster-quality" in df.columns:
        df = df.drop(columns=["cluster-quality"])

    aucs = df.sum(axis=0) / df.shape[0]
    print("AUCs")
    print(aucs)

    fig = plt.figure(figsize=(5, 3))
    plt.tight_layout()
    ax = plt.gca()
    # ax.set_title(f"{dataset}: {strategy_name(strategy)} strategy with {distance.upper()} and {linkage}")
    ax.grid(True, which="major", axis="y", ls=":", lw=1)
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
            label=measures["hierarchy-quality"],
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

    for measurement in df.columns:
        plt.plot(
            df.index,
            df[measurement],
            label=measures[measurement],
            lw=2,
            color=colors[measurement],
        )


    if use_runtime:
        ax.set_xlabel(f"Runtime ({runtime_unit})")
    else:
        ax.set_xlabel("Computational steps")
    ax.set_ylabel("Quality")
    ax.set_ylim(0.0, 1.05)
    ax.legend()
    fig.savefig(
        figures_dir / f"solutions-{dataset}-{distance}-{linkage}-{strategy}.pdf",
        bbox_inches="tight",
    )
    plt.show()


if __name__ == "__main__":
    main(sys.argv[1:])
