#!/usr/bin/env python3
import argparse
import sys

import numpy as np

from pathlib import Path
from tqdm import tqdm

from aeon.datasets.tsc_datasets import (
    univariate_equal_length,
    univariate_variable_length,
)
from aeon.datasets import load_classification


DATA_FOLDER = Path(__file__).resolve().parent.parent / "data" / "datasets"
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
        "-l",
        "--large",
        action="store_true",
        help="Download just the large datasets",
    )
    parser.add_argument(
        "-t",
        "--test",
        action="store_true",
        help="Download only the 10 small test datasets",
    )
    parser.add_argument(
        "-e",
        "--edeniss",
        action="store_true",
        help="Only use edeniss datasets",
    )
    parser.add_argument(
        "--datasets", type=str, nargs="+", help="List of datasets to download"
    )
    parser.add_argument(
        "-s",
        "--sorted",
        action="store_true",
        help="Sort the datasets by estimated processing time ascending",
    )

    return parser.parse_args(args)


def _sort_datasets(datasets):
    try:
        dataset_order = np.loadtxt(
            DATA_FOLDER.parent / "datasets.csv",
            delimiter=",",
            skiprows=1,
            usecols=(0,),
            dtype=str,
        ).tolist()
        return sorted(datasets, key=lambda x: dataset_order.index(x))
    except FileNotFoundError:
        print("Could not find datasets.csv. Sorting by name.", file=sys.stderr)
        return sorted(datasets)


def select_aeon_datasets(
    download_all=False, only_large=False, only_test=False, datasets=None, sorted=False
):
    # filter out long-running datasets
    if sum([download_all, only_large, only_test, datasets is not None]) > 1:
        raise ValueError(
            "Cannot download all, only large, only test, and just selected datasets."
        )

    if only_large:
        result = LONG_RUNNING_DATASETS

    elif only_test:
        result = SMALL_TEST_DATASETS

    else:
        all_datasets = list(univariate_equal_length) + list(univariate_variable_length)
        if datasets:
            unknown_datasets = set(datasets) - set(all_datasets)
            if unknown_datasets:
                raise ValueError(f"Unknown datasets: {', '.join(unknown_datasets)}")
            result = datasets
        elif not download_all:
            result = [d for d in all_datasets if d not in LONG_RUNNING_DATASETS]
        else:
            result = all_datasets

    if sorted:
        return _sort_datasets(result)
    return result


def select_edeniss_datasets(data_folder):
    path = data_folder / "edeniss20182020_anomalies"
    if path.exists() and path.is_dir():
        edeniss_datasets = sorted([f.stem for f in path.glob("*.ts")])
        return edeniss_datasets
    else:
        return []


def main(data_folder, datasets, skip_edeniss=False):
    data_folder = Path(data_folder).resolve()
    data_folder.mkdir(parents=True, exist_ok=True)

    if datasets:
        print(f"Downloading datasets to {data_folder} ...", file=sys.stderr)
        for dataset in tqdm(datasets):
            load_classification(dataset, extract_path=data_folder)
            print(dataset)
        print("... done.", file=sys.stderr)

    if not skip_edeniss:
        print("Searching for edeniss datasets ...", file=sys.stderr)
        edeniss_datasets = select_edeniss_datasets(data_folder)
        for dataset in edeniss_datasets:
            print(dataset)
        if edeniss_datasets:
            print("... found.", file=sys.stderr)
        else:
            print("... not found.", file=sys.stderr)


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    if args.edeniss:
        main(
            args.datafolder if args.datafolder else DATA_FOLDER,
            datasets=[],
            skip_edeniss=False,
        )
    else:
        datasets = select_aeon_datasets(
            args.all, args.large, args.test, args.datasets, args.sorted
        )
        main(
            args.datafolder if args.datafolder else DATA_FOLDER,
            datasets,
            skip_edeniss=args.test or args.datasets is not None,
        )
