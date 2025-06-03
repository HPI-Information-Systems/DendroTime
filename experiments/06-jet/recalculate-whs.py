#!/usr/bin/env python
import subprocess
import sys
import joblib

import pandas as pd
import numpy as np

from pathlib import Path
from tqdm import tqdm

sys.path.append(str(Path(__file__).resolve().parent.parent))
from download_datasets import DATA_FOLDER
from tqdm_joblib import tqdm_joblib

N_JOBS = -1
RESULT_FOLDER = Path("results")


def compute_whs(dataset, distance, data_folder):
    result_path = RESULT_FOLDER / "hierarchies" / f"hierarchy-{dataset}-{distance}.csv"
    target_path = (
        data_folder.parent / "ground-truth" / dataset / f"hierarchy-{distance}-ward.csv"
    )

    cmd = [
        "java",
        "-Dlogback.configurationFile=logback.xml",
        "-jar",
        "../DendroTime-Evaluator.jar",
        "weightedHierarchySimilarity",
        "--prediction",
        result_path.absolute().as_posix(),
        "--target",
        target_path.absolute().as_posix(),
    ]
    try:
        whs = float(
            subprocess.check_output(
                " ".join(cmd), shell=True, stderr=subprocess.DEVNULL
            )
            .decode("utf-8")
            .strip()
        )
    except Exception as e:
        print(f"Cannot compute WHS for {dataset} with {distance}: {e}")
        whs = np.nan
    return whs


def main():
    df = pd.read_csv(RESULT_FOLDER / "results.csv")
    df = df.set_index(["dataset", "distance"])

    configurations = df.index.values
    entries = []
    with tqdm_joblib(tqdm(desc="Processing", total=len(configurations))):
        entries = joblib.Parallel(n_jobs=N_JOBS)(
            joblib.delayed(compute_whs)(dataset, distance, DATA_FOLDER)
            for dataset, distance in configurations
        )
    df["whs"] = entries
    df.to_csv(RESULT_FOLDER / "results.csv")


if __name__ == "__main__":
    main()
