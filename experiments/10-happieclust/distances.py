from typing import Any, List, Optional, Tuple, Union
from joblib import Parallel, delayed
import numpy as np

from numba import njit
from tslearn.metrics import dtw
from scipy.signal import correlate

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
def chebyshev_distance(x: np.ndarray, y: np.ndarray) -> float:
    """Calculate the Chebyshev distance between two time series."""
    # use the minimum of the two series
    m = min(x.shape[0], y.shape[0])
    return float(np.max(np.abs(x[:m] - y[:m])))


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


@njit(cache=True, fastmath=True)
def _c(x_i: float, x_i_1: float, y_j: float, constant: float) -> float:
    if (x_i_1 <= x_i and x_i <= y_j) or (x_i_1 >= x_i and x_i >= y_j):
        return constant
    return float(constant + min(np.abs(x_i - x_i_1), np.abs(x_i - y_j)))


@njit(cache=True, fastmath=True)
def msm_distance(x: np.ndarray, y: np.ndarray, constant: Optional[float] = 0.5) -> float:
    constant = constant or 0.5
    m = x.shape[0]
    n = y.shape[0]

    cost = np.zeros((m, n), dtype=np.float_)
    cost[0, 0] = np.abs(x[0] - y[0])
    for i in range(1, m):
        cost[i, 0] = cost[i - 1, 0] + _c(x[i], x[i - 1], y[0], constant)

    for j in range(1, n):
        cost[0, j] = cost[0, j - 1] + _c(y[j], x[0], y[j - 1], constant)

    for i in range(1, m):
        for j in range(1, n):
            cost[i, j] = min(
                cost[i - 1, j - 1] + np.abs(x[i] - y[j]),
                cost[i - 1, j] + _c(x[i], x[i - 1], y[j], constant),
                cost[i, j - 1] + _c(y[j], x[i], y[j - 1], constant),
            )

    return float(cost[m - 1, n - 1])


def sbd_distance(x: np.ndarray, y: np.ndarray) -> float:
    return abs(
        float(
            1
            - np.max(
                correlate(x, y, method="fft") / np.sqrt(np.dot(x, x) * np.dot(y, y))
            )
        )
    )


distance_functions = {
    "euclidean": euclidean_distance,
    "lorentzian": lorentzian_distance,
    "sbd": sbd_distance,
    "msm": lambda x, y: msm_distance(x, y, constant=0.5),
    "dtw": dtw,
    "kdtw": lambda x, y: kdtw_distance(
        x, y, gamma=1.0, epsilon=1e-20, normalize_input=True, normalize_dist=True
    ),
    "chebyshev": chebyshev_distance,
}


def distance_pairs(
    series: Union[np.ndarray, List[np.ndarray]],
    pairs: Union[List[Tuple[int, int]], np.ndarray],
    distance_name: str = "euclidean",
    **kwargs: Any
) -> np.ndarray:
    n_jobs = kwargs.get("n_jobs", 1)
    distances = Parallel(n_jobs=n_jobs)(
        delayed(distance_functions[distance_name])(series[i], series[j]) for i, j in pairs  # only 1d metrics so far
    )
    return np.array(distances)


def matrix_other(
    series: Union[np.ndarray, List[np.ndarray]],
    other: Union[np.ndarray, List[np.ndarray]],
    distance_name: str = "euclidean",
    **kwargs: Any,
) -> np.ndarray:
    n_jobs = kwargs.get("n_jobs", 1)

    distance_matrix = Parallel(n_jobs=n_jobs)(
        delayed(distance_functions[distance_name])(series[i], other[j])  # only 1d metrics so far
        for i in range(len(series))
        for j in range(len(other))
    )
    return np.array(distance_matrix).reshape(len(series), len(other))
