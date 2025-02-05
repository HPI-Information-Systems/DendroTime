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
linkages=( "single" "complete" "average" "ward" )
# download datasets
datasets=$(python ../download_datasets.py --all)

# run experiments in n_jobs subprocesses
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
      if [[ $(jobs -r -p | wc -l) -ge $n_jobs ]]; then
          wait -n
      fi
    done
  done
done

# wait for the remaining jobs to finish
wait

# aggregate runtime results
python aggregate-runtimes.py

# create tar file
tar -czf 01-serial-hac-results.tar.gz results/*
echo "Results are stored in 01-serial-hac-results.tar.gz"

python create-runtimes-ratio-table.py
