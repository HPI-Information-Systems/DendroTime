#!/usr/bin/env python
import subprocess
import sys
import argparse
import time
import psutil

import numpy as np

from pathlib import Path

from aeon.datasets import load_classification, load_from_ts_file
from aeon.utils.validation import check_n_jobs

from jet import JET, JETMetric
from sklearn.metrics import adjusted_rand_score

sys.path.append(str(Path(__file__).resolve().parent.parent))

N_LOGICAL_CORES = psutil.cpu_count(logical=True)
distance_functions = {
    "sbd": JETMetric.SHAPE_BASED_DISTANCE,
    "msm": JETMetric.MSM,
    "dtw": JETMetric.DTW,
}


def parse_args(args):
    parser = argparse.ArgumentParser(description="Execute JET on all datasets.")
    parser.add_argument(
        "--data-folder",
        type=str,
        help="Set the folder, where the datasets are stored",
    )
    parser.add_argument(
        "--result-folder",
        type=str,
        help="Set the folder, where the results are stored",
    )
    parser.add_argument("--dataset", type=str, help="Dataset name")
    parser.add_argument(
        "--distance",
        type=str,
        default="sbd",
        choices=("sbd", "msm", "dtw"),
        help="Distance metric",
    )
    parser.add_argument(
        "--n-jobs",
        type=int,
        default=N_LOGICAL_CORES,
        help="Number of jobs to use",
    )
    return parser.parse_args(args)


def _load_edeniss_dataset(dataset, data_folder):
    path = f"{data_folder}/edeniss20182020_anomalies/{dataset}.ts"
    return load_from_ts_file(path)


def load_dataset(dataset, data_folder):
    if dataset.startswith("edeniss"):
        X, y = _load_edeniss_dataset(dataset, data_folder)
    else:
        X, y = load_classification(
            dataset, extract_path=data_folder, load_equal_length=False
        )
    n_clusters = len(np.unique(y))
    # we support only univariate time series
    X = [x.ravel() for x in X]
    return X, y, n_clusters


def main(data_folder, result_folder, dataset, distance, n_jobs):
    n_jobs = check_n_jobs(n_jobs)
    print(f"Using {n_jobs} jobs", file=sys.stderr)
    verbose = False

    (result_folder / "hierarchies").mkdir(exist_ok=True, parents=True)
    aggregated_result_file = result_folder / "results.csv"
    print(f"Storing results in {aggregated_result_file}", file=sys.stderr)
    with open(aggregated_result_file, "w") as f:
        f.write("dataset,distance,runtime,ARI\n")

    X, y, n_clusters = load_dataset(dataset, data_folder)
    t0 = time.time()
    try:
        jet = JET(
            n_clusters=n_clusters,
            n_pre_clusters=None,
            n_jobs=n_jobs,
            verbose=verbose,
            metric=distance_functions[distance],
            c=1.0,
        )
        jet.fit(X)
        h = jet._ward_clustering._linkage_matrix
        t1 = time.time()

        runtime = int((t1 - t0) * 1000)
        ari = adjusted_rand_score(y, jet.predict(X))

        np.savetxt(
            result_folder / "hierarchies" / f"hierarchy-{dataset}-{distance}.csv",
            h,
            delimiter=",",
        )
    except Exception as e:
        print(f"Error for {dataset} with {distance}: {e}")
        runtime = np.nan
        ari = np.nan
    with open(aggregated_result_file, "a") as f:
        f.write(f"{dataset},{distance},{runtime},{ari}\n")


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    data_folder = Path(args.data_folder)
    result_folder = Path(args.result_folder)
    dataset = args.dataset
    distance = distance_functions[args.distance]
    n_jobs = args.n_jobs
    main(data_folder, result_folder, dataset, distance, n_jobs=n_jobs)
