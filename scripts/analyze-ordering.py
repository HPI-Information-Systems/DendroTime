#!/usr/bin/env python3

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from collections import defaultdict
from pathlib import Path


result_dir = Path.cwd() / "experiments" / "ordering-strategy-analysis"
colors = defaultdict(lambda: "blue")
colors["fcfs"] = "green"
colors["shortestTs"] = "green"
colors["approxAscending"] = "red"
colors["approxDescending"] = "orange"
colors["highestVar"] = "purple"
colors["gtLargeError"] = "brown"
colors["dynamicError"] = "pink"


def main(dataset = "PickupGestureWiimoteZ", n = 5, seed = 2):
    print(f"Processing dataset '{dataset}' with {n} time series and seed {seed}")
    df = pd.read_csv(result_dir / f"traces-{n}-{dataset}-{seed}.csv", header=None)
    df_orderings = pd.read_csv(result_dir / f"orderings-{n}.csv", header=None)
    df_strategies = pd.read_csv(result_dir / f"strategies-{n}-{dataset}-{seed}.csv")

    aucs = df.sum(axis=1)/df.shape[1]
    df_strategies["auc"] = df_strategies["index"].apply(lambda i: aucs.loc[i])
    aucs = aucs.sort_values()

    print(f"Top 5 orderings (~{aucs.iloc[-1].item():.2f})")
    for i in aucs.index[-5:]:
        values = df_orderings.iloc[i].values
        tuples = list(zip(values[::2], values[1::2]))
        print(tuples)

    for _, (strategy, i, _, auc) in df_strategies.iterrows():
        print(f"{strategy} ({auc:.2f})")
        values = df_orderings.iloc[i].values
        tuples = list(zip(values[::2], values[1::2]))
        print(tuples)

    plt.figure()
    plt.title("Distribution of ordering quality")
    aucs.hist()
    for _, (strategy, i, _, auc) in df_strategies.iterrows():
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
    for _, (strategy, i, _, _) in df_strategies.iterrows():
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
