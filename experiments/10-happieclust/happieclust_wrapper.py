#!/usr/bin/env python
import time

import numpy as np
from aeon.datasets import load_classification, load_from_ts_file
from sklearn.metrics import adjusted_rand_score

from happieclust import HappieClust


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


def run_happieclust(dataset, distance, linkage, n_jobs, data_folder):
    verbose = False

    X, y, n_clusters = load_dataset(dataset, data_folder)
    t0 = time.time()
    happieclust = HappieClust(
        n_clusters=n_clusters,
        n_jobs=n_jobs,
        method=linkage,
        metric=distance,
        verbose=verbose,
        random_state=42,
    )
    h = happieclust._calculate_linkings(X)
    t1 = time.time()

    runtime = int((t1 - t0) * 1000)
    ari = adjusted_rand_score(y, happieclust._cut_tree(h, X))

    return h, runtime, ari
