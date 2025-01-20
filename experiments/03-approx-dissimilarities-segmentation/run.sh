#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

# create distribution plot with python
python create-dissimilarities-distribution-plot.py
