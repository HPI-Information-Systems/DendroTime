#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

distances=( "euclidean" "dtw" "msm" "sbd" )
linkages=( "single" "complete" "average" "weighted" )
# download datasets
# datasets=$(python ../download_datasets.py --all --sorted)
datasets="edeniss20182020_co2_anomalies
edeniss20182020_ec_anomalies
edeniss20182020_ics_anomalies
edeniss20182020_level_anomalies
edeniss20182020_par_anomalies
edeniss20182020_ph_anomalies
edeniss20182020_pressure_anomalies
edeniss20182020_rh_anomalies
edeniss20182020_temp_anomalies
edeniss20182020_valve_anomalies
edeniss20182020_volume_anomalies
edeniss20182020_vpd_anomalie
Crop
ElectricDevices
StarLightCurves"

# run experiments in n_jobs subprocesses
for dataset in $datasets; do
  for distance in "${distances[@]}"; do
    for linkage in "${linkages[@]}"; do
      echo ""
      echo ""
      echo "Processing dataset: $dataset, distance: $distance, linkage: $linkage"
      # allow it to fail without dragging down the whole script (|| true) and run it in background (&)
      java -Xmx56g -Dfile.encoding=UTF-8 \
        -Dlogback.configurationFile=../logback.xml \
        -Dconfig.file=application.conf \
        -jar ../DendroTime-runner.jar \
        --parallel --dataset "${dataset}" --distance "${distance}" --linkage "${linkage}" || true
    done
  done
done

# aggregate runtime results
# python aggregate-runtimes.py

# create tar file
# tar -czf 07-parallel-hac-results.tar.gz results/*
# echo "Results are stored in 07-parallel-hac-results.tar.gz"
