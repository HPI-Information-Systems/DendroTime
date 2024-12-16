# DendroTime
Progressive HAC system for time series anomalies

## Dev

Run DendroTime from sbt with

```sbt
run <hostname> <port>
```

Build a JAR for DendroTime using the `runner` SBt-subproject:

```sbt
project runner
assembly
```

The final JAR will be at `experiments/DendroTime-runner.jar`. On Linux, you can restrict the number of cores used
by DendroTime using `taskset -c <cpu-list>`.
