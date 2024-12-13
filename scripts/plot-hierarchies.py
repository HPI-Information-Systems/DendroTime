#!/usr/bin/env python3
import argparse
import sys

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from collections import defaultdict
from pathlib import Path

from scipy.stats import skewnorm
from scipy.spatial.distance import squareform
from scipy.cluster import hierarchy
from scipy.cluster.hierarchy import dendrogram, cut_tree
from sklearn.metrics import adjusted_rand_score, jaccard_score
from aeon.datasets import load_classification


colors = defaultdict(lambda: "blue")
colors["fcfs"] = "green"
colors["shortestTs"] = "cyan"
colors["approxAscending"] = "red"
colors["approxDescending"] = "orange"
colors["highestVar"] = "purple"
colors["gtLargestPairError"] = "brown"
colors["gtLargestTsError"] = "darkgray"
colors["approxDiffTsError"] = "olive"
# those names changed in the new version:
colors["dynamicError"] = "pink"
colors["approxFullError"] = "pink"


def parse_args(args):
    parser = argparse.ArgumentParser(description="Plot hierarchies of a DendroTime execution to debug issues.")
    parser.add_argument("target_experiment", type=str,
                        help="The target experiment strategy CSV file.")
    parser.add_argument("--data-folder", type=str, default="data/datasets",
                        help="The folder where the data is stored.")

    return parser.parse_args(args)


def parse_order(order):
    values = order.split(" ")
    tuples = list(eval(v) for v in values)
    return tuples


def main(sys_args):
    args = parse_args(sys_args)
    target_experiment_file = Path(args.target_experiment).resolve()
    data_folder = Path(args.data_folder).resolve()

    # parse result file name
    filename = target_experiment_file.stem
    if not filename.startswith("strategies"):
        raise ValueError("The filename must start with 'strategies'")

    dataset = filename.split("-")[-1]
    distance = filename.split("-")[1]
    linkage = filename.split("-")[2]

    result_dir = target_experiment_file.parent
    quality_measure = result_dir.stem.split("-")[-1].split(".")[0]
    if quality_measure not in ["ari", "target_ari", "hierarchy", "weighted", "averageari"]:
        raise ValueError(f"Unknown quality measure '{quality_measure}' in result directory name '{result_dir.stem}'")

#     plot_hierarchies(result_dir, data_folder, filename, dataset, distance, linkage)
    plot_distances(result_dir, data_folder, filename, dataset, distance, linkage)


def _hierarchy_similarity(h1, h2):
    clusters1 = set(np.sort(np.unique(x)).data.tobytes() for x in _hierarchy_clusters(h1))
    clusters2 = set(np.sort(np.unique(x)).data.tobytes() for x in _hierarchy_clusters(h2))
    return _set_jaccard_similarity(clusters1, clusters2)


def _weighted_hierarchy_similarity(h1, h2):
    clusters1 = _hierarchy_clusters(h1)
    clusters2 = _hierarchy_clusters(h2)
    n = len(clusters1)

    similarities = np.ones((n, n), dtype=float)
    for i in range(n):
        for j in range(i, n):
            similarities[i, j] = _set_jaccard_similarity(clusters1[i], clusters2[j])
            if i != j:
                similarities[j, i] = similarities[i, j]

    aggregated = _aggregate_greedy(similarities)
    return aggregated


def _hierarchy_clusters(h):
    n = len(h) + 1
    clusters = np.empty(n + n - 1, dtype=set)
    clusters[:n] = [set([x]) for x in range(n)]
    for i in range(n - 1):
        clusters[n + i] = clusters[int(h[i, 0])] | clusters[int(h[i, 1])]

    return clusters[n:]


def _set_jaccard_similarity(s1, s2):
    return len(s1 & s2) / len(s1 | s2)


def _aggregate_greedy(sims):
    sum_similarity = 0.0
    matched = set()
    for i in range(sims.shape[0]):
      maxId = 0
      maxValue = 0.0
      for j in range(sims.shape[0]):
        if j not in matched and sims[i, j] > maxValue:
          maxId = j
          maxValue = sims[i, j]
      sum_similarity += maxValue
      matched.add(maxId)

    return sum_similarity / sims.shape[0]


def plot_hierarchies(result_dir, data_dir, filename, dataset, distance, linkage):
    print(f"Loading debug hierarchies for dataset {dataset}, distance {distance}, linkage {linkage}...")
    hierarchies = {}
    for f in result_dir.iterdir():
        if f.is_file() and f.stem.startswith(f"hierarchy-{distance}-{linkage}-{dataset}") and f.suffix == ".csv":
            step = int(f.stem.split("-")[-1].replace("step", ""))
            hierarchies[step] = pd.read_csv(f, header=None).values

    idx = np.sort(list(hierarchies.keys()))
    print(f"... loaded {len(hierarchies)} hierarchies.")

    print("Loading dataset to compute quality measures...")
    X, y = load_classification(dataset, extract_path=data_dir, load_equal_length=False)
    n = len(X)
    m = n * (n - 1) / 2
    target_hierarchy = hierarchies[m]
    target_hierarchy_labels = cut_tree(target_hierarchy, n_clusters=20).flatten()
    df_quality = pd.DataFrame(index=idx, columns=["ARI", "target-ARI", "hierarchy-similarity", "weighted-hierarchy-similarity"])
    for i in idx:
        hierarchy = hierarchies[i]
        labels = cut_tree(hierarchy, n_clusters=len(np.unique(y))).flatten()
        df_quality.loc[i, "ARI"] = adjusted_rand_score(y, labels)
        labels = cut_tree(hierarchy, n_clusters=20).flatten()
        df_quality.loc[i, "target-ARI"] = adjusted_rand_score(target_hierarchy_labels, labels)
        df_quality.loc[i, "hierarchy-similarity"] = _hierarchy_similarity(hierarchy, target_hierarchy)
        df_quality.loc[i, "weighted-hierarchy-similarity"] = _weighted_hierarchy_similarity(hierarchy, target_hierarchy)
    print(df_quality)

    plt.figure()
    plt.title("Solutions")
    index = np.arange(0, df_quality.shape[0])
    plt.plot(index, df_quality["weighted-hierarchy-similarity"], color="blue", label="weighted-hierarchy-similarity")
#     for _, row in df_strategies.iterrows():
#         strategy = row["strategy"]
#         i = row["index"]
#         color = colors[strategy]
#         plt.plot(index, df.iloc[i, :], color=color, label=strategy)
#     plt.xlabel(f"Available distances (of {n*(n-1)/2})")
#     if "ari" in quality_measure:
#         plt.ylabel("Adjusted Rand Index")
#         plt.ylim(-0.55, 1.05)
#     else:
    plt.ylabel("Hierarchy Quality")
    plt.ylim(-0.05, 1.05)

    plt.legend(ncol=2)
    plt.show()
    return

    fig, axs = plt.subplots(len(hierarchies), 1, figsize=(10, 20))
    leaves = []
    for i, ax in enumerate(axs):
        ax.set_title(f"Step {idx[i]}")
        r = dendrogram(hierarchies[idx[i]], ax=ax, orientation="right", count_sort="ascending")
        leaves.append(r["ivl"])
    for i in range(1, len(leaves)):
        if not np.array_equal(leaves[i], leaves[i - 1]):
            print(f"Leaves at step {idx[i]} differ from step {idx[i - 1]}")
            for j in range(len(leaves[i])):
                if leaves[i][j] != leaves[i - 1][j]:
                    print(f"position {j}: {leaves[i][j]} != {leaves[i - 1][j]}")
    fig.tight_layout()
    plt.show()


def plot_distances(result_dir, data_dir, filename, dataset, distance, linkage):
    print(f"Loading distance matrix for dataset {dataset} and distance {distance}")
    dists = pd.read_csv(result_dir / f"distances-{distance}-{dataset}.csv", header=None).values
    print(dists)

    print(f"Computing target hierarchy for dataset {dataset}, distance {distance}, linkage {linkage}...")
    X = squareform(dists, force="tovector", checks=False)
    h = hierarchy.linkage(X, method=linkage)
    n_clusters = 5
    target_hierarchy_labels = cut_tree(h, n_clusters=n_clusters).flatten()
    # map labels to colors
    colors = np.array([f"C{i+1}" for i in range(n_clusters)])
    target_hierarchy_colors = colors[target_hierarchy_labels]

    import networkx as nx
    G = nx.Graph()
    G.add_nodes_from(range(len(dists)))
    for i in range(len(dists)):
        for j in range(i + 1, len(dists)):
            G.add_edge(i, j, weight=-dists[i, j])

    pos = nx.spring_layout(G, iterations=5000)
    plt.figure()
    nx.draw(G, pos, with_labels=True, node_color=target_hierarchy_colors, edge_color=(0.0, 0.0, 0.0, 0.2))
#     labels = nx.get_edge_attributes(G, 'weight')
#     nx.draw_networkx_edge_labels(G, pos, edge_labels=labels)

    fig = plt.figure()
    ax = plt.gca()
    ax.set_title("Target Dendrogram")
    dendrogram(h, ax=ax, orientation="right", count_sort="ascending")
    fig.tight_layout()
    plt.show()


if __name__ == "__main__":
    main(sys.argv[1:])
