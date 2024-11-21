# Scripts

## Scala Worksheets

You can execute the scala worksheets in this directory with [scala-cli](https://scala-cli.virtuslab.org).

For some scripts you may need the built project dependencies.
You can use sbt to build the DendroTime project and publish the different modules to the local ivy repository.
Just run this command from the project root directoy:

```shell
sbt "bloom-filter/publishLocal; progress-bar/publishLocal; backend/publishLocal"
```

Then, you can run the scripts with the following command:

```shell
scala-cli scripts/script.sc
```

## Python scripts

Dependencies are listed in the `requirements.txt` file.
You can install them with the following command:

```shell
pip install -r requirements.txt
```

Then, you can run the scripts with the following command:

```shell
python scripts/script.py
```
