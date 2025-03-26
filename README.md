<div align="center">
<img width="100px" src="https://github.com/HPI-Information-Systems/DendroTime/raw/main/dendrotime-icon.png" alt="" onerror="this.style.display='none'"/>
<h1 align="center">DendroTime</h1>
<p>
Progressive HAC system for time series anomalies.
</p>

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Scala version 3.3](https://img.shields.io/badge/Scala-3.3-blue)

</div>

---

Most effective dissimilarity measures for variable-length time series, such as Dynamic Time Warping (DTW) or Move-Split-Merge (MSM), are expensive to compute because their runtimes increase quadratically with the time series' lengths.
Because hierarchical agglomerative clustering (HAC) algorithms need to compute all pairwise time series dissimilarities, they are slow and do not scale to large time series collections.
However, there are use cases, where fast, interactive hierarchical clustering is necessary.
For these use cases, progressive clustering algorithms can improve runtimes and interactivity.
Progressive algorithms are incremental algorithms that produce and continuously improve an approximate solution, which eventually converges to the exact solution.

We present DendroTime, the first (parallel) progressive clustering system for variable-length time series collections.
The system incrementally computes the pairwise dissimilarities between the input time series and supports different ordering strategies to achieve progressivity.
Our evaluation demonstrates that DendroTime's progressive strategies are very effective for clustering scenarios with expensive time series dissimilarity computations.

### Architecture

DendroTime consists of a reactive client-server architecture that executes the progressive HAC algorithm on a collection of time series (anomalies) in a highly concurrent execution server and visualizes dendrogram updates in a web-based client.
The execution server is implemented using the [_actor programming model_](), which is a reactive programming paradigm for concurrent and parallel applications.
Its core primitives are _actors_, which are objects with private state and behavior.
The following figures provides an overview of DendroTime's client-server architecture and its actors:

![DendroTime architecture](./docs/figures/dendrotime-architecture.png)

DendroTime supports the following measures to compute the dissimilarities between time series:

- Minkowsky distances (i.a., Euclidean)
- MSM
- DTW
- SBD

The following linkage functions are supported and compatible:

- _single_
- _complete_
- _average_
- _weighted_

Other linkage functions, such as Ward or centroid linkage, are not compatible with non-metric time series dissimilarity measures, but can be enabled in code:
[`de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage`](dendrotime-clustering/src/main/scala/de/hpi/fgis/dendrotime/clustering/hierarchy/Linkage.scala).
The client is web-based and visualizes the dendrogram as well as the computational and qualitative progress to allow the user to monitor the clustering results over time, and stop the process early.

## Repository structure

| **Folder** | **Description** |
| :--------- | :-------------- |
| [`bloom-filter`]() ||
| [`progress-bar`]() ||
| [`dendrotime-clustering`]() ||
| [`dendrotime-io`]() ||
| [`dendrotime-frontend`]() ||
| [`dendrotime-backend`]() ||
| [`dendrotime-runner`]() ||
| [`dendrotime-evaluator`]() ||
| [`dendrotime-benchmarking`]() | Contains jmh micro-benchmarks. |
| [`data`]() ||
| [`docs`]() ||

## Citation

When using this software please cite our paper:

> tbd

## Installation

Please make sure that you have a recent Java runtime environment on your machine.

### Building

#### Prerequisites

- Git
- Java >= 21.0.0
- SBT >= 1.10.0
- (Scala 3 is managed by SBT)
- Node.js >= v22.12.0
- npm >= 11.0.0

#### Procedure

1. Clone the GitHub repository

   ```bash
   git clone git@github.com:HPI-Information-Systems/DendroTime.git
    ```

2. Build the whole project and create the runtime artifacts.
   The web server version (backend + frontend) is built using:

   ```bash
   sbt assembly
   ```

   If you just want to build the CLI-version use:

   ```bash
   sbt runner/assembly
   ```

   You can find the fat-jar for the DendroTime runner in the `experiments`-folder.

### Usage of the web application

Run DendroTime from sbt with

```sbt
run <hostname> <port>
```

Build a JAR for DendroTime using the `runner` SBT-subproject:

```sbt
project runner
assembly
```

The final JAR will be at `experiments/DendroTime-runner.jar`.
On Linux, you can restrict the number of cores used by DendroTime using `taskset -c <cpu-list>`.

### Usage from the CLI

### Algorithm configuration

## Experiments & Reproducibility
