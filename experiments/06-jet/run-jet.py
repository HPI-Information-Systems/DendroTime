#!/usr/bin/env python
import sys
import argparse
import time
import psutil

import numpy as np

from pathlib import Path

from aeon.datasets import load_classification, load_from_ts_file
from aeon.utils.validation import check_n_jobs

from jet import JET, JETMetric

sys.path.append(str(Path(__file__).resolve().parent.parent))
from download_datasets import DATA_FOLDER

RESULT_FOLDER = Path("results")
distance_functions = {
    "sbd": JETMetric.SHAPE_BASED_DISTANCE,
    "msm": JETMetric.MSM,
    "dtw": JETMetric.DTW,
}
DEFAULT_N_JOBS = check_n_jobs(psutil.cpu_count(logical=False))


def parse_args(args):
    parser = argparse.ArgumentParser(description="Execute JET.")
    parser.add_argument(
        "--datafolder",
        type=str,
        help="Overwrite the folder, where the datasets are stored",
    )
    parser.add_argument(
        "--dataset",
        type=str,
        help="Target dataset",
    )
    parser.add_argument(
        "--distance",
        type=str,
        default="sbd",
        choices=["sbd", "msm", "dtw"],
        help="Distance function to use",
    )
    parser.add_argument(
        "--n_jobs",
        type=int,
        default=DEFAULT_N_JOBS,
        help="Number of jobs to use for parallel processing",
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


def main(data_folder, dataset, distance, n_jobs):
    print(f"Using {n_jobs} jobs")
    verbose = False

    X, _, n_clusters = load_dataset(dataset, data_folder)
    try:
        t0 = time.time()
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
        print(f"JET took {runtime:.2f} seconds to process {dataset} with {distance}")

        print(f"Storing hierarchy at {result_path}")
        np.savetxt(result_path, h, delimiter=",")
    except Exception as e:
        print(f"Error for {dataset} with {distance}: {e}")
        runtime = np.nan


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    data_folder = args.datafolder if args.datafolder else DATA_FOLDER
    dataset = args.dataset
    distance = args.distance
    n_jobs = args.n_jobs

    result_path = RESULT_FOLDER / "hierarchies" / f"hierarchy-{dataset}-{distance}.csv"
    result_path.parent.mkdir(exist_ok=True, parents=True)

    main(data_folder, dataset, distance, n_jobs)
