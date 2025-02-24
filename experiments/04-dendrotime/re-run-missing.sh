#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

# this script exposes a single positional argument
strategy="${1-approx-distance-ascending}"

# ignore sbd because we compute them in another job
distances=( "euclidean" "dtw" "msm" )
linkages=( "single" "complete" "average" "ward" )
failure_log_file="results/${strategy}-failures.csv"
mkdir -p "results"
if [ ! -f "${failure_log_file}" ]; then
  echo "strategy,dataset,distance,linkage,code" > "${failure_log_file}"
fi

# select datasets
datasets="FordA
NonInvasiveFetalECGThorax2
NonInvasiveFetalECGThorax1
MixedShapesRegularTrain
HandOutlines
UWaveGestureLibraryAll
Crop
ElectricDevices
StarLightCurves
"

# run experiments one after the other
for dataset in $datasets; do
  for distance in "${distances[@]}"; do
    for linkage in "${linkages[@]}"; do
      echo ""
      echo ""
      echo "Processing dataset: $dataset, distance: $distance, linkage: $linkage"
      exit_code=0
      java -Xmx32g -Dfile.encoding=UTF-8 \
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

# create tar file
echo ""
echo "FINISHED!"
