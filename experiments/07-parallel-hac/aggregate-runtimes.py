#!/usr/bin/env python3
import sys

from pathlib import Path
from tqdm import tqdm

import numpy as np
import pandas as pd

from dataclasses import dataclass
from scipy.hierarchy import cut_tree
from sklearn.metrics import adjusted_rand_score
from aeon.datasets import load_classification

DATA_FOLDER = Path("../data/datasets")
RESULT_FOLDER = Path("results")


@dataclass
class Experiment:
    dataset: str
    distance: str
    linkage: str
    strategy: str = "parallel"


def parse_experiment_name(f):
    parts = f.stem.split("-")
    return Experiment(*parts)


def evaluate_clustering(exp, file):
    try:
        _, y = load_classification(exp.dataset, extract_path=DATA_FOLDER, load_equal_length=False)
        n_clusters = len(np.unique(y))
        Z = np.loadtxt(file / "parallel" / "hierarchy.csv", delimiter=",")
        clusters = cut_tree(Z, n_clusters=n_clusters).flatten()
        return adjusted_rand_score(y, clusters)
    except (FileNotFoundError, ValueError) as e:
        print(f"Failed to compute quality for {exp}: {e}")
        return np.nan


def main():
    experiments = [f for f in RESULT_FOLDER.iterdir() if f.is_dir()]
    print(
        f"Processing results from {len(experiments)} experiments ...", file=sys.stderr
    )
    entries = []
    for file in tqdm(experiments):
        exp = parse_experiment_name(file)
        exp_runtimes = pd.read_csv(file / "parallel" / "runtimes.csv")
        ari = evaluate_clustering()
        for phase, runtime in exp_runtimes.itertuples(index=False):
            entries.append(
                (exp.dataset, exp.distance, exp.linkage, exp.strategy, phase, runtime, ari)
            )
    df = pd.DataFrame(
        entries,
        columns=["dataset", "distance", "linkage", "strategy", "phase", "runtime", "ARI"],
    )
    df.to_csv(RESULT_FOLDER / "aggregated-runtimes.csv", index=False)
    print("... done.", file=sys.stderr)


if __name__ == "__main__":
    main()
