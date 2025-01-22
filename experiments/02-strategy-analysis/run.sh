#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

distances=( "euclidean" "dtw" "msm" "sbd" )
linkages=( "single" "complete" "average" "ward" )
datasets=(
    # variable length
    "PickupGestureWiimoteZ" "ShakeGestureWiimoteZ"
    # equal length
    "BirdChicken" "BeetleFly" "Coffee" "Beef" "Wine" "Meat" "Lightning2" "Lightning7"
)
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
      if [[ $(jobs -r -p | wc -l) -ge $N ]]; then
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
