#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

distances=( "euclidean" "dtw" "msm" "sbd" )
linkages=( "single" "complete" "average" "ward" )
# download datasets
datasets=$(python download-all-datasets.py)
# set parallelization factor to number of physical cores
N=$(lscpu -p | grep -v '^#' | cut -d, -f2 | sort -n | uniq | wc -l)
echo "Number of physical cores: $N"

# run experiments in N subprocesses
for dataset in $datasets; do
  for distance in "${distances[@]}"; do
    for linkage in "${linkages[@]}"; do
      echo ""
      echo ""
      echo "Processing dataset: $dataset, distance: $distance, linkage: $linkage"
      java -Xmx16g -Dfile.encoding=UTF-8 \
        -Dlogback.configurationFile=../logback.xml \
        -Dconfig.file=application.conf \
        -jar ../DendroTime-runner.jar --serial --dataset "${dataset}" --distance "${distance}" --linkage "${linkage}" &

      # allow to execute up to $N jobs in parallel
      if [[ $(jobs -r -p | wc -l) -ge $N ]]; then
          wait -n
      fi
    done
  done
done

# wait for the remaining jobs to finish
wait

# aggregate runtime results
python aggregate-runtimes.py
