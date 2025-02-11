#!/usr/bin/env python3
import sys

import matplotlib.pyplot as plt
import numpy as np

from aeon.datasets import load_from_ts_file
from matplotlib.patches import FancyArrowPatch
from pathlib import Path
from sklearn.preprocessing import MinMaxScaler

sys.path.append(str(Path(__file__).resolve().parent.parent))
from plt_commons import cm

DATA_FOLDER = Path("data")
FIGURE_FOLDER = Path("figures")
FIGURE_FOLDER.mkdir(exist_ok=True)

thresholds = [0.1131, 0.1062, 0.0957]


def main():
    FIGURE_FOLDER.mkdir(exist_ok=True)
    print("Loading time series")
    timeseries = []
    for ts_file in DATA_FOLDER.glob("time_series*.ts"):
        X, _ = load_from_ts_file(ts_file)
        for x in X:
            if sum(x.shape) < 10000:
                timeseries.append(
                    MinMaxScaler().fit_transform(x.reshape(-1, 1)).ravel()
                )

    print("Loading scores")
    scores = []
    for ts_file in DATA_FOLDER.glob("scores*.ts"):
        X, _ = load_from_ts_file(ts_file)
        for x in X:
            if sum(x.shape) < 10000:
                scores.append(MinMaxScaler().fit_transform(x.reshape(-1, 1)).ravel())

    # we cut them out below
    # print("Loading anomalies")
    # anomalies = []
    # for ts_file in DATA_FOLDER.glob("anomaly*.ts"):
    #     X, _ = load_from_ts_file(ts_file)
    #     anomalies.extend(X)
    # anomalies = [ts.ravel() for ts in anomalies]

    print("Loading done, plotting ...")

    # create single time series plots
    print("  - time series")
    for i, ts in enumerate(timeseries):
        fig, ax = plt.subplots(figsize=(3, 0.75), dpi=300)
        ax.plot(ts, lw=2, color=cm(1))
        ax.axis("off")
        fig.savefig(FIGURE_FOLDER / f"time-series-{i}.pdf", bbox_inches="tight")
        plt.close(fig)

    # create plot of time series with scores
    print("  - time series with scores")
    for i in range(len(timeseries)):
        ts = timeseries[i]
        score = scores[i]
        fig, axs = plt.subplots(2, 1, sharex="all", figsize=(3, 1.5), dpi=300)
        axs[0].plot(ts, lw=2, color=cm(1))
        axs[0].axis("off")
        axs[1].plot(score, lw=2, color=cm(6))
        spines = axs[1].spines
        spines["top"].set_visible(False)
        spines["right"].set_visible(False)
        axs[1].set_xticks([])
        axs[1].set_xticklabels([])
        fig.savefig(FIGURE_FOLDER / f"time-series-scores-{i}.pdf", bbox_inches="tight")
        plt.close(fig)

    # create plot of time series with scores and threshold
    print("  - time series with scores and threshold")
    anomalies = []
    # i = 0
    for i in range(len(timeseries)):
        ts = timeseries[i]
        score = scores[i]
        threshold = thresholds[i // 2]
        anomalies.append([])
        tmp = np.r_[0, score >= threshold, 0]
        anomaly_bounds = np.c_[
            np.nonzero(np.diff(tmp) == 1)[0], np.nonzero(np.diff(tmp) == -1)[0]
        ]
        fig, axs = plt.subplots(3, 1, sharex="all", figsize=(3, 2.25), dpi=300)
        axs[0].plot(ts, lw=2, color=cm(1))
        axs[0].axis("off")

        axs[1].plot(score, lw=2, color=cm(6))
        axs[1].axhline(threshold, color=cm(4), lw=1, ls="--")
        for b, e in anomaly_bounds:
            rect = plt.Rectangle(
                (b, 0),
                e - b,
                2 + fig.subplotpars.wspace,
                color=cm(7),
                alpha=0.6,
                transform=axs[1].get_xaxis_transform(),
                clip_on=False,
            )
            axs[1].add_patch(rect)
            arrow = FancyArrowPatch(
                ((b + e) / 2, 0),
                ((b + e) / 2, -0.5),
                lw=1,
                color=cm(0),
                transform=axs[1].get_xaxis_transform(),
                clip_on=False,
                mutation_scale=10,
                arrowstyle="->",
            )
            axs[1].add_patch(arrow)
        spines = axs[1].spines
        spines["top"].set_visible(False)
        spines["right"].set_visible(False)
        axs[1].set_xticks([])
        axs[1].set_xticklabels([])

        indices = np.arange(ts.shape[0])
        for b, e in anomaly_bounds:
            axs[2].plot(indices[b:e], ts[b:e], lw=2, color=cm(1))
            anomalies[i].append(ts[b:e])
        axs[2].axis("off")
        fig.savefig(
            FIGURE_FOLDER / f"time-series-anomalies-{i}.pdf", bbox_inches="tight"
        )
        plt.close(fig)

    # create plots of anomalies
    print("  - anomalies")
    for i in range(len(anomalies)):
        for j, ts in enumerate(anomalies[i]):
            fig, ax = plt.subplots(figsize=(1, 0.75), dpi=300)
            ax.plot(ts, lw=2, color=cm(1))
            ax.axis("off")
            fig.savefig(FIGURE_FOLDER / f"anomaly-{i}-{j}.pdf", bbox_inches="tight")
            plt.close(fig)
    print("... done.")


if __name__ == "__main__":
    main()
