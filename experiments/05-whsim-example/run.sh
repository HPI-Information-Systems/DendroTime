#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

distances=( "euclidean" "dtw" "msm" "sbd" )
linkages=( "single" "complete" "average" )
strategies=( "fcfs" "approx-distance-ascending" "pre-clustering" )
# download datasets
datasets=$(python ../download_datasets.py --datasets ACSF1)

# run experiments one after the other
mkdir -p results
for dataset in "${datasets[@]}"; do
  for distance in "${distances[@]}"; do
    for linkage in "${linkages[@]}"; do
      for strategy in "${strategies[@]}"; do
        echo ""
        echo ""
        echo "Processing dataset: $dataset, distance: $distance, linkage: $linkage with strategy: $strategy"
        java -Xmx16g -Dfile.encoding=UTF-8 \
          -Dlogback.configurationFile=../logback.xml \
          -Dconfig.file=application.conf \
          -jar ../DendroTime-runner.jar \
            --dataset "${dataset}" --distance "${distance}" --linkage "${linkage}" \
            --strategy "${strategy}";
        python create-whsim-plot.py --dataset "${dataset}" --distance "${distance}" \
                                    --linkage "${linkage}" --strategy "${strategy}"
      done
    done
  done
done

# create tar file
tar -czf 05-whsim-example-results.tar.gz results/*
echo "Results are stored in 05-whsim-example-results.tar.gz"
