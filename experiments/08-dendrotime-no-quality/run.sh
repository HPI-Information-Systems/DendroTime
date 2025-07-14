#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

# this script exposes a single positional argument
strategy="${1-approx-distance-ascending}"
distance="${2-dtw}"

#distances=( "euclidean" "dtw" "msm" "sbd" )
linkages=( "single" "complete" "average" "weighted" "ward" )
failure_log_file="results/${distance}-${strategy}-failures.csv"
mkdir -p "results"
if [ ! -f "${failure_log_file}" ]; then
  echo "strategy,dataset,distance,linkage,code" > "${failure_log_file}"
fi

# download datasets
#datasets=$(python ../download_datasets.py --all --sorted)
datasets="
SemgHandGenderCh2 ✅,✅
SemgHandMovementCh2 ✅,✅
InlineSkate ✅,✅
SemgHandSubjectCh2 ✅,✅
EthanolLevel ✅,✅
HandOutlines ✅,✅
CinCECGTorso ✅,✅
Phoneme ✅,✅
Mallat ✅,✅
MixedShapesRegularTrain ✅,✅
MixedShapesSmallTrain ✅,✅
FordA ✅,✅
FordB ✅,✅
NonInvasiveFetalECGThorax1 ✅,✅
NonInvasiveFetalECGThorax2 ✅,✅
UWaveGestureLibraryAll ❌
UWaveGestureLibraryX ✅,✅
UWaveGestureLibraryY ✅,✅
UWaveGestureLibraryZ ✅,✅
Yoga ✅,✅
--------------------------
StarLightCurves ❌
Crop ❌
ElectricDevices ❌"

""" Missing DendroTime quality datasets:
SemgHandGenderCh2
SemgHandMovementCh2
InlineSkate
--- (scheduled)
Phoneme
Mallat
MixedShapesRegularTrain (move to end)
MixedShapesSmallTrain
FordA
FordB
NonInvasiveFetalECGThorax1
NonInvasiveFetalECGThorax2
UWaveGestureLibraryAll  (move to end)
UWaveGestureLibraryY
UWaveGestureLibraryZ
UWaveGestureLibraryX
Yoga
--- (end scheduled)
StarLightCurves
Crop
ElectricDevices

Parallel runtimes larger than 1h (in s):
-------------------------------------
(StarLightCurves              711996)
(UWaveGestureLibraryAll       144172)
HandOutlines                  114914
MixedShapesRegularTrain        71899
NonInvasiveFetalECGThorax1     63771
NonInvasiveFetalECGThorax2     63617
MixedShapesSmallTrain          53499
Mallat                         48065
FordA                          46476
CinCECGTorso                   43797
FordB                          37963
Phoneme                        37748 >10h (12+2 datasets)
EthanolLevel                   24667
(ElectricDevices               15229)
SemgHandMovementCh2            14167
SemgHandSubjectCh2             14164
SemgHandGenderCh2              14142
Yoga                           13954
InlineSkate                    12186
UWaveGestureLibraryZ           11932
UWaveGestureLibraryX           11920
UWaveGestureLibraryY           11909 >3h (22+1 datasets)
(Crop                           7671)
Wafer                           7119
EOGVerticalSignal               6573
EOGHorizontalSignal             6560
FreezerRegularTrain             4687
FreezerSmallTrain               4314
"""

# run experiments one after the other
for dataset in $datasets; do
  for linkage in "${linkages[@]}"; do
    echo ""
    echo ""
    echo "Processing dataset: $dataset, distance: $distance, linkage: $linkage, strategy: $strategy"
    exit_code=0
    java -Xmx48g -Dfile.encoding=UTF-8 \
      -Dlogback.configurationFile=../logback.xml \
      -Dconfig.file=application.conf \
      -jar ../DendroTime-runner.jar \
        --dataset "${dataset}" --distance "${distance}" --linkage "${linkage}" \
        --strategy "${strategy}" || exit_code=$?
    # record failures in a separate file
    echo "${strategy},${dataset},${distance},${linkage},${exit_code}" >> "${failure_log_file}"
  done
done

echo ""
echo "===================="
echo "Finished"
echo "===================="
