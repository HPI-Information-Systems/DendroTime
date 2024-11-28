#!/usr/bin/env python
import sys
import argparse

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from pathlib import Path

from aeon.datasets import load_classification
from aeon.distances import distance
from aeon.utils.validation import check_n_jobs

from jet.feature_encoder import FeatureEncoder
from jet.pre_clustering import PreClustering

cmap = plt.get_cmap("tab10")


def parse_args(args):
    parser = argparse.ArgumentParser(description="Load a dataset and execute the first two stages of JET (feature "
                                     "extraction and preclustering) to produce a preclustering.")
    parser.add_argument("dataset", type=str,
                        help="The dataset to process.")
    parser.add_argument("--dataset_folder", type=str, default="data/datasets",
                        help="Folder containing the aeon datasets.")
    parser.add_argument("--result_folder", type=str, default="experiments/preclustering",
                        help="Folder containing the aeon datasets.")

    return parser.parse_args(args)


def main(sys_args):
    args = parse_args(sys_args)
    dataset = args.dataset
    dataset_folder = Path(args.dataset_folder)
    result_folder = Path(args.result_folder)
    compute_stats(dataset, dataset_folder, result_folder)


def compute_stats(dataset, dataset_folder, result_folder):
    n_jobs = check_n_jobs(-1)
    verbose = True
    n_pre_clusters = None

    print(f"Loading dataset from {dataset_folder}")
    X, y = load_classification(dataset, extract_path=dataset_folder, load_equal_length=False)
    # we support only univariate time series
    X = [x.ravel() for x in X]

    def distance_fun(x, y):
        return distance(x, y, metric="msm")

    feature_encoder = FeatureEncoder(n_jobs=n_jobs, verbose=verbose)
    pre_clustering = PreClustering(n_clusters=n_pre_clusters, n_jobs=n_jobs, verbose=verbose)

    print("Computing preclustering")
    X_encoded = feature_encoder.fit_transform(X)
    pre_labels = pre_clustering.fit_predict(X_encoded)
    n_pre_clusters_ = len(np.unique(pre_labels))
    print(f"Found {n_pre_clusters_} pre-clusters")
    print(pre_labels)

    print("Saving preclustering")
    result_folder.mkdir(parents=True, exist_ok=True)
    preclustering_file = result_folder / f"{dataset}-prelabels.csv"
    np.savetxt(preclustering_file, pre_labels, fmt="%d")


if __name__ == "__main__":
    main(sys.argv[1:])
