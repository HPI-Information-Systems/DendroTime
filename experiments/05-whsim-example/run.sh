#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

distances=( "euclidean" "dtw" "msm" "sbd" )
linkages=( "single" "complete" "average" "ward" )
datasets=( "ACSF1" )
strategies=( "fcfs" "approx-distance-ascending" "pre-clustering" )

# set parallelization factor to number of physical cores
N=$(lscpu -p | grep -v '^#' | cut -d, -f2 | sort -n | uniq | wc -l)
echo "Number of physical cores: $N"

# download datasets
echo "Downloading datasets ..."
for dataset in "${datasets[@]}"; do
  echo "  ${dataset}"
  python -c "from aeon.datasets import load_classification; load_classification('${dataset}', extract_path='../../data/datasets/')"
done
echo "... done."

# run experiments in N subprocesses
mkdir -p results
for dataset in "${datasets[@]}"; do
  for distance in "${distances[@]}"; do
    for linkage in "${linkages[@]}"; do
      for strategy in "${strategies[@]}"; do
        echo ""
        echo ""
        echo "Processing dataset: $dataset, distance: $distance, linkage: $linkage with strategy: $strategy"
        (
          java -Xmx16g -Dfile.encoding=UTF-8 \
            -Dlogback.configurationFile=../logback.xml \
            -Dconfig.file=application.conf \
            -jar ../DendroTime-runner.jar \
              --dataset "${dataset}" --distance "${distance}" --linkage "${linkage}" \
              --strategy "${strategy}";
          python create-whsim-plot.py --dataset "${dataset}" --distance "${distance}" \
                                      --linkage "${linkage}" --strategy "${strategy}"
        ) &

        # allow to execute up to $N jobs in parallel
        if [[ $(jobs -r -p | wc -l) -ge $N ]]; then
            wait -n
        fi
      done
    done
  done
done

# wait for the remaining jobs to finish
wait

# create tar file
tar -czf 05-whsim-example-results.tar.gz results/*
echo "Results are stored in 05-whsim-example-results.tar.gz"
