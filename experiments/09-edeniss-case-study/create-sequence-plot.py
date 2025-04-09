#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2025 German Aerospace Center (DLR.e.V.)
# SPDX-FileContributor: Ferdinand Rewicki <ferdinand.rewicki@dlr.de>
#
# SPDX-License-Identifier: MIT

import argparse
import math
import sys

import matplotlib.pyplot as plt
import matplotlib.colors as mcolors
import numpy as np

from aeon.datasets import load_from_ts_file
from pathlib import Path
from scipy.cluster.hierarchy import fcluster

sys.path.append(str(Path(__file__).parent.parent))


def main(sys_args):
    dataset = "edeniss20182020_ics_anomalies_1min"
    strategy = "approx_distance_ascending"
    distance = "dtw"
    linkage="centroid"

    plot_results(dataset, strategy, distance, linkage)

def plot_results(dataset, strategy, distance, linkage):
    X, y = load_from_ts_file(f'datasets/edeniss20182020_anomalies/{dataset}.ts',)
    X = [x[0] for x in X]
    max_len = max(len(a) for a in X)
    X_padded = np.array([np.pad(x, (0, max_len - len(x)), constant_values=np.nan) for x in X])
    Z = np.genfromtxt(
        f'./ground-truth/{dataset}/hierarchy-{distance}-{linkage}-{strategy}.partial.csv',
        delimiter=",")
    y = fcluster(Z, 40, criterion='maxclust')

    classmap = {
        12: 0,
        14: 3,
        23: 4,
        28: 8,
        32: 2,
        33: 1,
        6: 10,
        8: 10,
        22: 11,
    }
    labelmap = {
        12: (0.20, 0.2),
        33: (0.75, 0.7),
        32: (0.75, 0.7),
        14: (0.20, 0.2),
        23: (0.18, 0.7),
        28: (0.20, 0.2),
        6: (0.85, 0.75),
        8: (0.8, 0.2),
        22: (0.8, 0.7),
    }
    fig, ax = _create_plot(X_padded, y, classmap, labelmap, fontsize=22)
    fig.savefig('edeniss-clusters-40.pdf')

def _create_plot(X, y, classmap, labelmap, xticklabels = None, fontsize = 14):
    clusters = np.unique(y)
    cmap = _get_cmap()
    n_cols = min(len(clusters), 8)
    n_rows = math.ceil(len(clusters) / 8)
    fig, ax = plt.subplots(
        ncols=n_cols, nrows=n_rows, figsize=(n_cols * 1.4, n_rows * 0.8), sharey=False
    )

    if n_cols == 1 and n_rows == 1:
        ax = np.array([ax]).reshape(-1, 1)
    if (n_cols == 1 or n_rows == 1) and not (n_cols == 1 and n_rows == 1):
        ax = np.array([ax])
    for ci, c in enumerate(clusters):
        cidx = ci % n_cols
        ridx = int(ci / n_cols)
        for xx in X[y == c]:
            ax[ridx, cidx].plot(xx.ravel(), "k-", alpha=0.2)

        if xticklabels is not None:
            ax[ridx, cidx].set_xticks(np.arange(len(xticklabels)))
            ax[ridx, cidx].set_xticklabels(
                xticklabels, rotation=45, ha="right", fontsize=fontsize
            )

    for ci, c in enumerate(clusters):
        cidx = ci % n_cols
        ridx = int(ci / n_cols)
        ax[ridx, cidx].set_yticks([])
        ax[ridx, cidx].set_xticks([])
        if ci + 1 in classmap:
            text = f'#{classmap[ci + 1]}' if classmap[ci + 1] < 10 else f'N{classmap[ci + 1] - 9}'
            ax[ridx, cidx].text(
                labelmap[ci + 1][0],
                labelmap[ci + 1][1],
                text,
                fontsize=fontsize,
                transform=ax[ridx, cidx].transAxes,
                ha='center',
                va='center',
                c='black'
            )
        if ci + 1 in classmap:
            ax[ridx, cidx].patch.set_facecolor(cmap(classmap[ci + 1]))
            ax[ridx, cidx].patch.set_alpha(0.5)
    fig.tight_layout(rect=[0, 0.03, 1, 0.95])
    plt.subplots_adjust(wspace=0.05, hspace=0.05)

    return fig, ax

def _get_cmap():
    nipy_colors = [plt.cm.nipy_spectral(i / 9) for i in range(10)]
    custom_colors = [(0.978422, 0.557937, 0.034931),
                     (0.735683, 0.215906, 0.330245)]
    all_colors = nipy_colors + custom_colors
    custom_cmap = mcolors.ListedColormap(all_colors, name="custom_nipy10_plus")

    return custom_cmap

if __name__ == "__main__":
    main(sys.argv[1:])
