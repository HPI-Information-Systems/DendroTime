#!/usr/bin/env python3
import sys
import joblib

from pathlib import Path
from tqdm import tqdm

import numpy as np
import pandas as pd

from dataclasses import dataclass
from scipy.cluster.hierarchy import cut_tree
from sklearn.metrics import adjusted_rand_score
from aeon.datasets import load_classification

sys.path.append(str(Path(__file__).resolve().parent.parent))
from tqdm_joblib import tqdm_joblib

DATA_FOLDER = Path("../data/datasets")
RESULT_FOLDER = Path("results")
N_JOBS = 4


@dataclass
class Experiment:
    dataset: str
    distance: str
    linkage: str
    strategy: str = "serial"


def _parse_experiment_name(f):
    parts = f.stem.split("-")
    return Experiment(*parts)


def _evaluate_clustering(exp, file):
    ari = np.nan
    if not exp.dataset.startswith("edeniss"):
        try:
            _, y = load_classification(
                exp.dataset, extract_path=DATA_FOLDER, load_equal_length=False
            )
            n_clusters = len(np.unique(y))
            Z = np.loadtxt(file / "serial" / "hierarchy.csv", delimiter=",")
            clusters = cut_tree(Z, n_clusters=n_clusters).flatten()
            ari = adjusted_rand_score(y, clusters)
        except (FileNotFoundError, ValueError) as e:
            print(f"Failed to compute quality for {exp}: {e}")
    return ari


def extract_results(file):
    exp = _parse_experiment_name(file)
    exp_runtimes = pd.read_csv(file / "serial" / "runtimes.csv")
    ari = _evaluate_clustering(exp, file)
    entries = []
    for phase, runtime in exp_runtimes.itertuples(index=False):
        entries.append(
            (exp.dataset, exp.distance, exp.linkage, exp.strategy, phase, runtime, ari)
        )
    return entries


def main():
    experiments = [f for f in RESULT_FOLDER.iterdir() if f.is_dir()]
    print(
        f"Processing results from {len(experiments)} experiments ...", file=sys.stderr
    )
    entries = []
    with tqdm_joblib(tqdm(desc="Processing", total=len(experiments))):
        entries = joblib.Parallel(n_jobs=N_JOBS)(
            joblib.delayed(extract_results)(f) for f in experiments
        )

    df = pd.DataFrame(
        [e for batch in entries for e in batch],
        columns=[
            "dataset",
            "distance",
            "linkage",
            "strategy",
            "phase",
            "runtime",
            "ARI",
        ],
    )
    df.to_csv(RESULT_FOLDER / "aggregated-runtimes.csv", index=False)
    print("... done.", file=sys.stderr)


if __name__ == "__main__":
    main()
