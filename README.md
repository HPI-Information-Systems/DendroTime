<div align="center">
<img width="100px" src="https://github.com/HPI-Information-Systems/DendroTime/raw/main/dendrotime-icon.png" alt="DendroTime logo"/>
<h1 align="center">DendroTime</h1>
<p>
Progressive HAC system for time series anomalies.
</p>

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Scala version 3.3](https://img.shields.io/badge/Scala-3.3-blue)

</div>

## Abstract

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
