#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

# set parallelization factor based on number of physical cores and installed memory
N=$(lscpu -p | grep -v '^#' | cut -d, -f2 | sort -n | uniq | wc -l)
TOTAL_MEM=$(lsmem -b --summary | grep 'Total online memory' | cut -d: -f2 | sed -e 's/^[[:space:]]*//')
let "required_mem = 16 * 1024 * 1024 * 1024"  # 16 GB
echo "Number of physical cores: $N"
echo "Memory in bytes (required / installed): $required_mem / $TOTAL_MEM"
let "mem_jobs = TOTAL_MEM / required_mem"
n_jobs=$((mem_jobs < N ? mem_jobs : N))
echo "Running $n_jobs jobs in parallel"

distances=( "euclidean" "dtw" "msm" "sbd" )
linkages=( "single" "complete" "average" "weighted" "ward" )
# download test datasets
datasets=$(python ../download_datasets.py --test)

# run experiments in n_jobs subprocesses
mkdir -p results
for dataset in $datasets; do
  for distance in "${distances[@]}"; do
    for linkage in "${linkages[@]}"; do
      echo ""
      echo ""
      echo "Processing dataset: $dataset, distance: $distance, linkage: $linkage"
      java -Xmx8g -Dfile.encoding=UTF-8 -jar testStrategies.jar \
        --resultFolder results/ \
        --dataFolder ../../data/datasets/ \
        --qualityMeasure weightedHierarchySimilarity \
        --distance "${distance}" \
        --linkage "${linkage}" \
        "${dataset}" &

      # allow to execute up to $N jobs in parallel
      if [[ $(jobs -r -p | wc -l) -ge $n_jobs ]]; then
          wait -n
      fi
    done
  done
done

# wait for the remaining jobs to finish
wait

# create tar file
tar -czf 02-strategy-analysis-results.tar.gz results/*
echo "Results are stored in 02-strategy-analysis-results.tar.gz"

# create plots for some configurations
# - msm with ward-linkage
python create-strategy-qualities-plot.py results/msm-ward-weightedHierarchySimilarity
# - msm with average-linkage
python create-strategy-qualities-plot.py results/msm-average-weightedHierarchySimilarity
# - sbd with average-linkage
python create-strategy-qualities-plot.py results/sbd-average-weightedHierarchySimilarity
# - dtw with average-linkage
python create-strategy-qualities-plot.py results/dtw-average-weightedHierarchySimilarity
