#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

distances=( "dtw" "msm" "sbd" )
# download datasets
datasets=$(python ../download_datasets.py --all --sorted)

# run experiments in n_jobs subprocesses
for dataset in $datasets; do
  for distance in "${distances[@]}"; do
    echo ""
    echo ""
    echo "Executing JET for dataset: $dataset and distance: $distance"
    # allow it to fail without dragging down the whole script (|| true) and run it in background (&)
    python run-jet.py --data-folder "../data/datasets/" --result-folder "results/" --dataset "${dataset}" --distance "${distance}" --linkage "${linkage}" || true
  done
done

# compute WHS
tmp_file="results/tmp-results.csv"
cat results/results.csv | while read line; do
  if [[ $line == "dataset"* ]]; then
    echo "$line,WHS" > $tmp_file
    continue
  fi
  dataset=$(echo $line | cut -d, -f1)
  distance=$(echo $line | cut -d, -f2)

  echo ""
  echo ""
  echo "Computing WHS for dataset: $dataset and distance: $distance"
  whs=$(java -jar ../DendroTime-Evaluator.jar weightedHierarchySimilarity --prediction "results/hierarchies/hierarchy-${dataset}-${distance}.csv" --target "../data/ground-truth/${dataset}/hierarchy-${distance}-ward.csv" || echo "NaN")
  echo "WHS for dataset: $dataset and distance: $distance is $whs"
  echo "$line,$whs" >> $tmp_file
done

# create tar file
tar -czf 06-jet-results.tar.gz results/*
echo "Results are stored in 06-jet-results.tar.gz"
