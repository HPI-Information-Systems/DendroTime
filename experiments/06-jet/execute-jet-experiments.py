#!/usr/bin/env python
import subprocess
import sys
import argparse
import time
import psutil

import numpy as np

from pathlib import Path
from tqdm import tqdm

from aeon.datasets import load_classification, load_from_ts_file
from aeon.utils.validation import check_n_jobs

from jet import JET, JETMetric
from sklearn.metrics import adjusted_rand_score

sys.path.append(str(Path(__file__).resolve().parent.parent))
from download_datasets import DATA_FOLDER, select_aeon_datasets, select_edeniss_datasets

RESULT_FOLDER = Path("results")
distance_functions = {
    "sbd": JETMetric.SHAPE_BASED_DISTANCE,
    "msm": JETMetric.MSM,
    "dtw": JETMetric.DTW,
}


def parse_args(args):
    parser = argparse.ArgumentParser(description="Execute JET on all datasets.")
    parser.add_argument(
        "--datafolder",
        type=str,
        help="Overwrite the folder, where the datasets are stored",
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


def compute_whs(dataset, distance, data_folder):
    result_path = RESULT_FOLDER / "hierarchies" / f"hierarchy-{dataset}-{distance}.csv"
    target_path = (
        data_folder.parent / "ground-truth" / dataset / f"hierarchy-{distance}-ward.csv"
    )

    cmd = [
        "java",
        "-jar",
        "../DendroTime-Evaluator.jar",
        "weightedHierarchySimilarity",
        "--prediction",
        result_path.absolute().as_posix(),
        "--target",
        target_path.absolute().as_posix(),
    ]
    try:
        whs = float(
            subprocess.check_output(" ".join(cmd), shell=True).decode("utf-8").strip()
        )
    except Exception as e:
        whs = np.nan
    return whs


def main(data_folder):
    n_jobs = check_n_jobs(psutil.cpu_count(logical=False))
    print(f"Using {n_jobs} jobs")
    verbose = False
    distances = ("sbd", "msm", "dtw")
    datasets = select_aeon_datasets(download_all=True, sorted=True)
    datasets = datasets + select_edeniss_datasets(data_folder)

    (RESULT_FOLDER / "hierarchies").mkdir(exist_ok=True, parents=True)
    aggregated_result_file = RESULT_FOLDER / "results.csv"
    print(f"Storing results in {aggregated_result_file}")
    with open(aggregated_result_file, "w") as f:
        f.write("dataset,distance,runtime,ARI,WHS\n")

    for dataset in tqdm(datasets):
        X, y, n_clusters = load_dataset(dataset, data_folder)
        for distance in distances:
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
                ari = adjusted_rand_score(y, jet.predict(X))

                np.savetxt(
                    RESULT_FOLDER
                    / "hierarchies"
                    / f"hierarchy-{dataset}-{distance}.csv",
                    h,
                    delimiter=",",
                )
            except Exception as e:
                print(f"Error for {dataset} with {distance}: {e}")
                runtime = np.nan
                ari = np.nan

            try:
                whs = compute_whs(dataset, distance, data_folder)
            except Exception as e:
                print(f"Cannot compute WHS for {dataset} with {distance}: {e}")
                whs = np.nan

            with open(aggregated_result_file, "a") as f:
                f.write(f"{dataset},{distance},{runtime},{ari},{whs}\n")


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    main(args.datafolder if args.datafolder else DATA_FOLDER)
