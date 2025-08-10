#!/usr/bin/env python
import time

import numpy as np
from aeon.datasets import load_classification, load_from_ts_file
from jet import JET, JETMetric
from jet_clustering_overwrite import LinkageClustering
from numba import njit
from sklearn.metrics import adjusted_rand_score

_eps = np.finfo(np.float64).eps


@njit(cache=True, fastmath=True)
def euclidean_distance(x: np.ndarray, y: np.ndarray) -> float:
    """Calculate the Euclidean distance between two time series."""
    # use the minimum of the two series
    m = min(x.shape[0], y.shape[0])
    return float(np.linalg.norm(x[:m] - y[:m]))


@njit(cache=True, fastmath=True)
def lorentzian_distance(x: np.ndarray, y: np.ndarray) -> float:
    """Calculate the Lorentzian distance between two time series."""
    # use the minimum of the two series
    m = min(x.shape[0], y.shape[0])
    return np.sum(np.log(1 + np.abs(x[:m] - y[:m])))


@njit(cache=True, fastmath=True)
def _normalize_time_series(x: np.ndarray) -> np.ndarray:
    return x - np.mean(x) / (np.std(x) + _eps)


@njit(cache=True, fastmath=True)
def _local_kernel(
    x: np.ndarray, y: np.ndarray, gamma: float, epsilon: float
) -> np.ndarray:
    # 1 / c in the paper; beta on the website
    factor = 1.0 / 3.0
    n_cases = x.shape[0]
    m_cases = y.shape[0]
    distances = np.zeros((n_cases, m_cases))

    for i in range(n_cases):
        for j in range(m_cases):
            distances[i, j] = (x[i] - y[j]) ** 2

    return factor * (np.exp(-distances / gamma) + epsilon)


@njit(cache=True, fastmath=True)
def _kdtw_cost_matrix(
    x: np.ndarray, y: np.ndarray, gamma: float, epsilon: float
) -> np.ndarray:
    local_kernel = _local_kernel(x, y, gamma, epsilon)

    # For the initial values of the cost matrix, we add 1
    n = np.shape(x)[-1] + 1
    m = np.shape(y)[-1] + 1

    cost_matrix = np.zeros((n, m))
    cumulative_dp_diag = np.zeros((n, m))
    diagonal_weights = np.zeros(max(n, m))

    # Initialize the diagonal weights
    min_timepoints = min(n, m)
    diagonal_weights[0] = 1.0
    for i in range(1, min_timepoints):
        diagonal_weights[i] = local_kernel[i - 1, i - 1]

    # Initialize the cost matrix and cumulative dp diagonal
    cost_matrix[0, 0] = 1
    cumulative_dp_diag[0, 0] = 1

    # - left column
    for i in range(1, n):
        cost_matrix[i, 0] = cost_matrix[i - 1, 0] * local_kernel[i - 1, 0]
        cumulative_dp_diag[i, 0] = cumulative_dp_diag[i - 1, 0] * diagonal_weights[i]

    # - top row
    for j in range(1, m):
        cost_matrix[0, j] = cost_matrix[0, j - 1] * local_kernel[0, j - 1]
        cumulative_dp_diag[0, j] = cumulative_dp_diag[0, j - 1] * diagonal_weights[j]

    # Perform the main dynamic programming loop
    for i in range(1, n):
        for j in range(1, m):
            local_cost = local_kernel[i - 1, j - 1]
            cost_matrix[i, j] = (
                cost_matrix[i - 1, j]
                + cost_matrix[i, j - 1]
                + cost_matrix[i - 1, j - 1]
            ) * local_cost
            cumulative_dp_diag[i, j] = (
                cumulative_dp_diag[i - 1, j] * diagonal_weights[i]
                + cumulative_dp_diag[i, j - 1] * diagonal_weights[j]
            )
            if i == j:
                cumulative_dp_diag[i, j] += (
                    cumulative_dp_diag[i - 1, j - 1] * local_cost
                )

    # Add the cumulative dp diagonal to the cost matrix
    cost_matrix = cost_matrix + cumulative_dp_diag
    return cost_matrix[1:, 1:]


@njit(cache=True, fastmath=True)
def kdtw_distance(
    x: np.ndarray,
    y: np.ndarray,
    gamma: float,
    epsilon: float,
    normalize_input: bool,
    normalize_dist: bool,
) -> float:
    """Calculate the KDTW distance between two time series."""
    if normalize_input:
        _x = _normalize_time_series(x)
        _y = _normalize_time_series(y)
    else:
        _x = x
        _y = y

    n = _x.shape[-1] - 1
    m = _y.shape[-1] - 1
    current_cost = _kdtw_cost_matrix(_x, _y, gamma, epsilon)[n, m]
    if normalize_dist:
        self_x = _kdtw_cost_matrix(_x, _x, gamma, epsilon)[n, n]
        self_y = _kdtw_cost_matrix(_y, _y, gamma, epsilon)[m, m]
        norm_factor = np.sqrt(self_x * self_y)
        if norm_factor != 0.0:
            current_cost /= norm_factor
    return 1.0 - current_cost


distance_functions = {
    "euclidean": JETMetric(euclidean_distance),
    "lorentzian": JETMetric(lorentzian_distance),
    "sbd": JETMetric.SHAPE_BASED_DISTANCE,
    "msm": JETMetric.MSM,
    "dtw": JETMetric.DTW,
    "kdtw": JETMetric(
        lambda x, y: kdtw_distance(
            x, y, gamma=1.0, epsilon=1e-20, normalize_input=True, normalize_dist=True
        )
    ),
}


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


def run_jet(data_folder, dataset, distance="sbd", linkage="ward", n_jobs=1):
    verbose = False

    X, y, n_clusters = load_dataset(dataset, data_folder)
    t0 = time.time()
    jet = JET(
        n_clusters=n_clusters,
        n_pre_clusters=None,
        n_jobs=n_jobs,
        verbose=verbose,
        metric=distance_functions[distance],
        c=1.0,
    )
    if linkage != "ward":
        # overwrite internal clustering implementation to allow for other linkages than
        # ward linkage
        jet._ward_clustering = LinkageClustering(
            n_clusters=jet.n_clusters,
            linkage=linkage,
            n_jobs=jet.n_jobs,
            verbose=jet.verbose,
        )
    jet.fit(X)
    h = jet._ward_clustering._linkage_matrix
    t1 = time.time()

    runtime = int((t1 - t0) * 1000)
    ari = adjusted_rand_score(y, jet.predict(X))

    return h, runtime, ari
