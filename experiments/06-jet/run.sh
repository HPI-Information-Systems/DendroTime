#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

# run experiments in python
python execute-jet-experiments.py

# create tar file
tar -czf 06-jet-results.tar.gz results/*
echo "Results are stored in 06-jet-results.tar.gz"
