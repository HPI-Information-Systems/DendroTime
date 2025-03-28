#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

# this script exposes a single positional argument
strategy="${1-approx-distance-ascending}"

distances=( "euclidean" "dtw" "msm" "sbd" )
linkages=( "single" "complete" "average" "weighted" )
failure_log_file="results/failures.csv"
mkdir -p "results"
if [ ! -f "${failure_log_file}" ]; then
  echo "strategy,dataset,distance,linkage,code" > "${failure_log_file}"
fi

# download datasets
datasets=$(python ../download_datasets.py --all --sorted)

# run experiments one after the other
for dataset in $datasets; do
  for distance in "${distances[@]}"; do
    for linkage in "${linkages[@]}"; do
      echo ""
      echo ""
      echo "Processing dataset: $dataset, distance: $distance, linkage: $linkage"
      exit_code=0
      java -Xmx48g -Dfile.encoding=UTF-8 \
        -Dlogback.configurationFile=../logback.xml \
        -Dconfig.file=application.conf \
        -jar ../DendroTime-runner.jar \
          --dataset "${dataset}" --distance "${distance}" --linkage "${linkage}" \
          --strategy "${strategy}" || exit_code=$?
      # record failures in a separate file
      echo "${strategy},${dataset},${distance},${linkage},${exit_code}" >> "${failure_log_file}"
    done
  done
done

# aggregate runtime results
python aggregate-runtimes.py

# create tar file
tar -czf 04-dendrotime-results.tar.gz results/*
echo "Results are stored in 04-dendrotime-results.tar.gz"

python plot-qualities.py --dataset ACSF1 --use-runtime
python plot-qualities.py --dataset ACSF1 --use-runtime --strategy fcfs
