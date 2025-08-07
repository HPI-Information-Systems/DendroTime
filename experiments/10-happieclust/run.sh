#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

# run experiments in python
python execute-happieclust-experiments.py

# create tar file
tar -czf 10-happieclust-results.tar.gz results/*
echo "Results are stored in 10-happieclust-results.tar.gz"
