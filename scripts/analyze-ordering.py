#!/usr/bin/env python3

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy.stats import skewnorm
from collections import defaultdict
from pathlib import Path


result_dir = Path.cwd() / "experiments" / "ordering-strategy-analysis"
colors = defaultdict(lambda: "blue")
colors["fcfs"] = "green"
colors["shortestTs"] = "cyan"
colors["approxAscending"] = "red"
colors["approxDescending"] = "orange"
colors["highestVar"] = "purple"
colors["gtLargestPairError"] = "brown"
colors["gtLargestTsError"] = "darkgray"
# those names changed in the new version:
colors["dynamicError"] = "pink"
colors["approxFullError"] = "pink"


def main(dataset = "PickupGestureWiimoteZ", n = 5, seed = 1):
    print(f"Processing dataset '{dataset}' with {n} time series and seed {seed}")
    if seed is None:
        tracesPath = result_dir / f"traces-{n}-{dataset}.csv"
        orderingsPath = None
        strategiesPath = result_dir / f"strategies-{n}-{dataset}.csv"
    else:
        tracesPath = result_dir / f"traces-{n}-{dataset}-{seed}.csv"
        orderingsPath = result_dir / f"orderings-{n}.csv"
        strategiesPath = result_dir / f"strategies-{n}-{dataset}-{seed}.csv"
    df = pd.read_csv(tracesPath, header=None)
    df_strategies = pd.read_csv(strategiesPath)

    aucs = df.sum(axis=1)/df.shape[1]
    if "auc" not in df_strategies.columns:
        df_strategies["auc"] = df_strategies["index"].apply(lambda i: aucs.loc[i])
    aucs = aucs.sort_values()

    a, loc, scale = skewnorm.fit(aucs.values, loc=0.5, method="MLE")
    print(f"Skewnorm distribution: {a=}, {loc=}, {scale=}")
    dist = skewnorm(a, loc, scale)

    if orderingsPath is not None:
        df_orderings = pd.read_csv(result_dir / f"orderings-{n}.csv", header=None)
        print(f"Top 5 orderings (~{aucs.iloc[-1].item():.2f})")
        for i in aucs.index[-5:]:
            values = df_orderings.iloc[i].values
            tuples = list(zip(values[::2], values[1::2]))
            print(tuples)

    for _, row in df_strategies.iterrows():
        strategy = row["strategy"]
        i = row["index"]
        auc = row["auc"]
        print(f"{strategy} ({auc:.2f})")
        if "order" in row:
            values = row["order"].split(" ")
            tuples = list(eval(v) for v in values)
        elif orderingsPath is not None:
            values = df_orderings.iloc[i].values
            tuples = list(zip(values[::2], values[1::2]))
        print(tuples[:20])

    plt.figure()
    plt.title("Distribution of ordering quality")
    aucs.plot(kind="hist", density=1, bins=30, stacked=False, alpha=0.5, label="AUC histogram")
    x = np.linspace(0.0, 1.0, 1000)
    plt.plot(x, dist.pdf(x), "k-", lw=2,
        label=f"Skewed Normal Distribution (a={a:.2f}, loc={loc:.2f}, scale={scale:.2f})"
    )
    for _, row in df_strategies.iterrows():
        strategy = row["strategy"]
        auc = row["auc"]
        color = colors[strategy]
        plt.axvline(x=auc, linestyle="--", color=color, label=strategy)
    plt.xlabel("Solution quality")
    plt.ylabel("Frequency")
    plt.xlim(-0.1, 1.1)
    plt.legend()
    plt.savefig(result_dir / f"hist-{n}-{dataset}-{seed}.pdf", bbox_inches='tight')

    plt.figure()
    plt.title("Solutions")
    best_id = aucs.index[-1]
    worst_id = aucs.index[0]
    index = np.arange(0, df.shape[1])
    plt.plot(index, df.iloc[best_id, :], linestyle="--", color="black", label="Best ordering")
    plt.plot(index, df.iloc[worst_id, :], linestyle="--", color="black", label="Worst ordering")
    for _, row in df_strategies.iterrows():
        strategy = row["strategy"]
        i = row["index"]
        color = colors[strategy]
        plt.plot(index, df.iloc[i, :], color=color, label=strategy)
    plt.xlabel(f"Available distances (of {df.shape[1]-1})")
    plt.ylabel("Hierarchy Quality")
    plt.ylim(0, 1.1)
    plt.legend()
    plt.savefig(result_dir / f"solutions-{n}-{dataset}-{seed}.pdf", bbox_inches='tight')
    plt.show()

if __name__ == '__main__':
    main()
