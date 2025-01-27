#!/usr/bin/env python3
import argparse
import sys

from pathlib import Path
from tqdm import tqdm

from aeon.datasets.tsc_datasets import (
    univariate_equal_length,
    univariate_variable_length,
)
from aeon.datasets import load_classification


DATA_FOLDER = Path(__file__).resolve().parent / "data" / "datasets"
LONG_RUNNING_DATASETS = [
    "Crop",
    "ElectricDevices",
    "StarLightCurves",
    "FordA",
    "HandOutlines",
    "MixedShapesRegularTrain",
    "NonInvasiveFetalECGThorax1",
    "NonInvasiveFetalECGThorax2",
    "UWaveGestureLibraryAll",
    "FordB",
    "Mallat",
    "MixedShapesSmallTrain",
    "CinCECGTorso",
    "Phoneme",
    "EthanolLevel",
    "InlineSkate",
    "SemgHandGenderCh2",
    "SemgHandMovementCh2",
    "SemgHandSubjectCh2",
    "UWaveGestureLibraryX",
    "UWaveGestureLibraryY",
    "UWaveGestureLibraryZ",
    "Yoga",
    "ChlorineConcentration",
    "ECG5000",
    "EOGHorizontalSignal",
    "EOGVerticalSignal",
    "FreezerRegularTrain",
    "FreezerSmallTrain",
    "PigAirwayPressure",
    "PigArtPressure",
    "PigCVP",
    "ShapesAll",
    "TwoPatterns",
    "Wafer",
]
SMALL_TEST_DATASETS = [
    # variable length
    "PickupGestureWiimoteZ",
    "ShakeGestureWiimoteZ",
    # equal length
    "BirdChicken",
    "BeetleFly",
    "Coffee",
    "Beef",
    "Wine",
    "Meat",
    "Lightning2",
    "Lightning7",
]


def parse_args(args):
    parser = argparse.ArgumentParser(description="Ensure that dataset are available.")
    parser.add_argument(
        "--datafolder",
        type=str,
        help="Overwrite the folder, where the datasets are stored",
    )
    parser.add_argument(
        "-f",
        "--all",
        action="store_true",
        help="Download all datasets, even the large ones",
    )
    parser.add_argument(
        "-t",
        "--test",
        action="store_true",
        help="Download only the 10 small test datasets",
    )
    parser.add_argument(
        "--datasets", type=str, nargs="+", help="List of datasets to download"
    )

    return parser.parse_args(args)


def select_datasets(download_all=False, only_test=False, datasets=None):
    # filter out long-running datasets
    if (
        (download_all and only_test)
        or (download_all and datasets is not None)
        or (only_test and datasets is not None)
    ):
        raise ValueError("Cannot download all, only test, and just selected datasets.")

    if only_test:
        return SMALL_TEST_DATASETS

    all_datasets = list(univariate_equal_length) + list(univariate_variable_length)
    if datasets:
        unknown_datasets = set(datasets) - set(all_datasets)
        if unknown_datasets:
            raise ValueError(f"Unknown datasets: {', '.join(unknown_datasets)}")
        return datasets
    if not download_all:
        return [d for d in all_datasets if d not in LONG_RUNNING_DATASETS]
    return all_datasets


def main(data_folder, datasets, skip_edeniss=False):
    data_folder = Path(data_folder).resolve()
    print(f"Downloading datasets to {data_folder} ...", file=sys.stderr)
    data_folder.mkdir(parents=True, exist_ok=True)

    for dataset in tqdm(datasets):
        load_classification(dataset, extract_path=data_folder)
        print(dataset)
    print("... done.", file=sys.stderr)

    if not skip_edeniss:
        print("Searching for edeniss datasets ...", file=sys.stderr)
        path = data_folder / "edeniss20182020_anomalies"
        if path.exists() and path.is_dir():
            edeniss_datasets = [f.stem for f in path.glob("*.ts")]
            for dataset in edeniss_datasets:
                print(dataset)
            print("... found.", file=sys.stderr)
        else:
            print("... not found.", file=sys.stderr)


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    datasets = select_datasets(args.all, args.test, args.datasets)
    main(
        args.datafolder if args.datafolder else DATA_FOLDER,
        datasets,
        skip_edeniss=args.test or args.datasets is not None,
    )
