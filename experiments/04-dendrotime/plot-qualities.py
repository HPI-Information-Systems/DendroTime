#!/usr/bin/env python3
import argparse
import json
import sys

import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))
from plt_commons import measure_name_mapping, colors


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
        choices=["pre-clustering", "approx-distance-ascending"],
        help="Strategy name",
    )
    parser.add_argument(
        "--use-runtime",
        action="store_true",
        help="Use runtime instead of computational steps as x-axis.",
    )

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    use_runtime = args.use_runtime
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

    plot_results(results_file, use_runtime)


def extract_measures_from_config(config_file):
    with config_file.open("r") as fh:
        config = json.load(fh)
    obj = config["dendrotime"]["progress-indicators"]
    mapping = {}
    for name in ["hierarchy-similarity", "hierarchy-quality", "cluster-quality"]:
        mapping[name] = measure_name_mapping[obj[name]]
    return mapping


def plot_results(results_file, use_runtime=False):
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

    aucs = df.sum(axis=0) / df.shape[0]
    print(aucs)

    plt.figure()
    plt.title(f"{dataset}: {strategy} strategy with {distance}-{linkage}")
    plt.axvline(
        x=df.index[middle],
        color="gray",
        linestyle="--",
        label="Approx. $\\rightarrow$ Exact",
    )
    for measurement in df.columns:
        plt.plot(
            df.index,
            df[measurement],
            label=measures[measurement],
            lw=2,
            color=colors[measurement],
        )

    if use_runtime:
        plt.xlabel(f"Runtime ({runtime_unit})")
    else:
        plt.xlabel("Computational steps")
    plt.ylabel("Quality")
    plt.ylim(-0.05, 1.05)

    plt.legend(ncol=2)
    plt.savefig(
        figures_dir / f"solutions-{dataset}-{distance}-{linkage}-{strategy}.pdf",
        bbox_inches="tight",
    )
    plt.show()


if __name__ == "__main__":
    main(sys.argv[1:])
