#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

strategy="approx-distance-ascending"
dataset="edeniss20182020_ics_anomalies_1min"
distance="${1:-msm}"
linkage="${2:-weighted}"

echo "Processing dataset: $dataset, distance: $distance, linkage: $linkage, strategy: $strategy"
java -Xmx48g -Dfile.encoding=UTF-8 \
  -Dlogback.configurationFile=../logback.xml \
  -Dconfig.file=application.conf \
  -jar ../DendroTime-runner.jar \
    --dataset "${dataset}" --distance "${distance}" --linkage "${linkage}" \
    --strategy "${strategy}" || true

echo ""
echo "Done."
