#!/usr/bin/env python3
import sys

from pathlib import Path
from tqdm import tqdm

from aeon.datasets.tsc_datasets import (
    univariate_equal_length,
    univariate_variable_length
)
from aeon.datasets import load_classification

DATA_FOLDER = Path("../../data/datasets/")
DATA_FOLDER.mkdir(parents=True, exist_ok=True)
LONG_RUNNING_DATASETS = [
    "Crop", "ElectricDevices", "StarLightCurves", "FordA", "HandOutlines",
    "MixedShapesRegularTrain", "NonInvasiveFetalECGThorax1",
    "NonInvasiveFetalECGThorax2", "UWaveGestureLibraryAll", "FordB", "Mallat",
    "MixedShapesSmallTrain", "CinCECGTorso", "Phoneme", "EthanolLevel",
    "InlineSkate", "SemgHandGenderCh2", "SemgHandMovementCh2",
    "SemgHandSubjectCh2", "UWaveGestureLibraryX", "UWaveGestureLibraryY",
    "UWaveGestureLibraryZ", "Yoga", "ChlorineConcentration", "ECG5000",
    "EOGHorizontalSignal", "EOGVerticalSignal", "FreezerRegularTrain",
    "FreezerSmallTrain", "PigAirwayPressure", "PigArtPressure", "PigCVP",
    "ShapesAll", "TwoPatterns", "Wafer"
]

print(f"Downloading datasets to {DATA_FOLDER} ...", file=sys.stderr)
all_datasets = [d for d in univariate_equal_length + univariate_variable_length if d not in LONG_RUNNING_DATASETS]
for dataset in tqdm(all_datasets):
    load_classification(dataset, extract_path=DATA_FOLDER)
    print(dataset)
print("... done.", file=sys.stderr)

print("Searching for edeniss datasets ...", file=sys.stderr)
path = DATA_FOLDER / "edeniss20182020_anomalies"
if path.exists() and path.is_dir():
    edeniss_datasets = [f.stem for f in path.glob("*.ts")]
    for dataset in edeniss_datasets:
        print(dataset)
    all_datasets = all_datasets + edeniss_datasets
    print("... found.", file=sys.stderr)
else:
    print("... not found.", file=sys.stderr)
