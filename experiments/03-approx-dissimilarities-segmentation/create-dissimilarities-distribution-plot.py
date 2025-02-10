# /usr/bin/env python3
import sys
import numpy as np

import matplotlib.pyplot as plt

from pathlib import Path

from scipy.spatial.distance import squareform
from scipy.stats import norm
from scipy.special import erfinv

from aeon.datasets import load_classification
from aeon.distances import pairwise_distance

sys.path.append(str(Path(__file__).resolve().parent.parent))
from plt_commons import cm

RESULT_FOLDER = Path("results")
distance_method_params = {
    "euclidean": {},
    "sbd": {"standardize": False},
    "dtw": {"window": 0.05, "itakura_max_slope": None},
    "msm": {"c": 0.5, "window": 0.05, "itakura_max_slope": None, "independent": True},
    "twe": {"nu": 0.0001, "lmbda": 1},
    "shape-dtw": {"window": 0.05},
}


def main(dataset: str = "BeetleFly", distance: str = "dtw") -> None:
    RESULT_FOLDER.mkdir(exist_ok=True, parents=True)
    cache_path = RESULT_FOLDER / f"{dataset}_{distance}_dists.npy"

    if cache_path.exists():
        print(f"Loading cached distances from {cache_path}")
        dists = np.load(cache_path)
        print("... done.")
    else:
        print(f"(Down-)loading dataset {dataset}")
        X, y = load_classification(dataset, extract_path="../../data/datasets/")

        print(f"Computing pairwise {distance} distances ...")
        dists = pairwise_distance(
            X, method=distance, **distance_method_params[distance]
        )
        np.save(cache_path, dists)
        print("... done.")

    print("Estimating distribution parameters and fitting distribution ...")
    n = dists.shape[0]
    m = n * (n - 1) / 2
    n_segments = int(np.log(m) / np.log(2))
    if n_segments % 2 == 1:
        n_segments += 1
    dists = squareform(dists, force="tovector")
    mean = np.mean(dists)
    std = np.std(dists)

    x = np.linspace(np.min(dists), np.max(dists), 1000)
    y = norm.pdf(x, mean, std)
    print("...done.")

    print(f"Computing {n_segments + 1} pivot points for {n_segments} segments ...")
    pivot_factors = []
    pivots = []
    segments = []
    for i in range(-n_segments // 2, n_segments // 2 + 1):
        p = 1.0 / n_segments * i
        f = erfinv(2 * p) * np.sqrt(2)
        pivot_factors.append(f)
        pivots.append(mean + std * f)
    for i in range(1, len(pivots)):
        if i == 1:
            middle = (dists.min() + pivots[i]) / 2
        elif i == len(pivots) - 1:
            middle = (pivots[i - 1] + dists.max()) / 2
        else:
            middle = (pivots[i - 1] + pivots[i]) / 2
        size = np.count_nonzero((dists >= pivots[i - 1]) & (dists < pivots[i]))
        print(f"{i} ({pivots[i-1]:.2f}, {pivots[i]:.2f}]: {size:d}")
        segments.append((i, middle, size))
    print("... done.")

    fig, axs = plt.figure(figsize=(6.5, 2)), plt.gca()
    axs.hist(dists, bins=100, density=True, color="lightgray")
    axs.plot(x, y, lw=2, color=cm(1), label="Normal Distribution")
    axs.set_ylabel("Density")
    axs.set_yticks([])
    axs.set_yticklabels([])
    axs.set_ylim((0, axs.get_ylim()[-1] * 1.1))
    axs.set_xlim((dists.min(), dists.max()))
    axs.set_xlabel("Dissimilarity")
    axs.set_xticks(pivots[1:-1])
    axs.set_xticklabels(
        [
            "$\\mu$" if f == 0 else f"$\\mu{f:+.2f}\\sigma^2$"
            for f in pivot_factors[1:-1]
        ],
        rotation=45,
        ha="right",
        va="top",
    )
    axs.spines[["right", "top"]].set_visible(False)

    # add pivots
    axs.axvline(pivots[1], lw=2, color=cm(6), linestyle="-.", label="Pivots")
    for p in pivots[2:-1]:
        if p == mean:
            axs.axvline(mean, lw=2, color=cm(4), linestyle="--", label="Mean")
        else:
            axs.axvline(p, lw=2, color=cm(6), linestyle="-.")
    for i, x, size in segments:
        axs.text(
            x, axs.get_ylim()[-1], f"{size:d}", ha="center", va="top", fontweight="bold"
        )
    axs.legend()
    fig.savefig(f"{dataset}_{distance}_dists_distribution.pdf", bbox_inches="tight")

    # plt.tight_layout()
    # plt.show()



if __name__ == "__main__":
    main(dataset="BeetleFly", distance="dtw")
