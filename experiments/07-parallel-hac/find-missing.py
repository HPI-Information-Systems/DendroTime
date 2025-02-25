#!/usr/bin/env python
import sys
from pathlib import Path

import pandas as pd

sys.path.append(str(Path(__file__).resolve().parent.parent))
from download_datasets import DATA_FOLDER, select_aeon_datasets, select_edeniss_datasets

RESULT_FOLDER = Path("results")


def main():
    distances = ("euclidean", "dtw", "msm", "sbd")
    linkages = ("single", "complete", "average", "ward")
    datasets = select_aeon_datasets(download_all=True, sorted=True)
    datasets = datasets + select_edeniss_datasets(DATA_FOLDER)

    entries = []
    for dataset in datasets:
        for distance in distances:
            for linkage in linkages:
                entries.append((dataset, distance, linkage))
    df_expected = pd.DataFrame(entries, columns=["dataset", "distance", "linkage"])
    df_expected = df_expected.set_index(["dataset", "distance", "linkage"]).sort_index()

    df = pd.read_csv(RESULT_FOLDER / "aggregated-runtimes.csv")
    df = df[df["phase"] == "Finished"]
    df = df.drop(columns=["phase", "strategy"])
    df = df.set_index(["dataset", "distance", "linkage"]).sort_index()

    missing = df_expected.index.difference(df.index)
    missing = missing.to_frame().reset_index(drop=True)
    print(missing.groupby(["dataset"]).count())

    print()
    print("Missing configurations for StarLightCurves:")
    for i, row in missing[missing["dataset"] == "StarLightCurves"].iterrows():
        print(f"{row['dataset']}, {row['distance']}, {row['linkage']}")


if __name__ == "__main__":
    main()
