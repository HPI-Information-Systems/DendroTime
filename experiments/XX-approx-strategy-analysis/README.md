# Explore effect of different approximation strategies

This experiment uses a scala worksheet as the experiment script: `test-approximations.sc`.

You can execute the scala worksheets in this directory with [scala-cli](https://scala-cli.virtuslab.org).

## Executing the worksheet

For some scripts you may need the built project dependencies.
You can use sbt to build the DendroTime project and publish the different modules to the local ivy repository.
Just run this command from the project root directory:

```shell
sbt "bloom-filter/publishLocal; progress-bar/publishLocal; dendrotime-io/publishLocal; dendrotime-clustering/publishLocal; backend/publishLocal"
```

> **Attention!** Make sure to update the dependency versions at the top of the script file!

Then, you can run the experiment with the following command:

```shell
scala-cli scripts/test-approximations.sc Coffee --resultFolder approx-strategy-analysis/ --dataFolder ../../data/datasets/ --all true
```

Alternatively, to use the worksheet with the experiment script `approx-strategy-analysis.sh`, you need to build a fat JAR:

```shell
scala-cli --power package --assembly -f test-approximations.sc
```

This builds the `test-approximations.jar` required for the experiment.

## Result

There is overall not much difference in the ARI scores between the tested approximation strategies.
