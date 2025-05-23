#!/usr/bin/env python
import subprocess
import sys
import argparse
import psutil

import numpy as np

from pathlib import Path
from tqdm import tqdm

from aeon.utils.validation import check_n_jobs

sys.path.append(str(Path(__file__).resolve().parent.parent))
from download_datasets import DATA_FOLDER, select_aeon_datasets, select_edeniss_datasets

from jet_wrapper import distance_functions, run_jet

RESULT_FOLDER = Path("results")


def parse_args(args):
    parser = argparse.ArgumentParser(description="Execute JET on all datasets.")
    parser.add_argument(
        "--datafolder",
        type=str,
        help="Overwrite the folder, where the datasets are stored",
    )
    return parser.parse_args(args)


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


def main(data_folder):
    n_jobs = check_n_jobs(psutil.cpu_count(logical=False))
    print(f"Using {n_jobs} jobs")
    # distances = list(distance_functions.keys())
    distances = ("lorentzian",)
    datasets = select_aeon_datasets(download_all=True, sorted=True)
    datasets = datasets + select_edeniss_datasets(data_folder)

    (RESULT_FOLDER / "hierarchies").mkdir(exist_ok=True, parents=True)
    aggregated_result_file = RESULT_FOLDER / "results.csv"
    print(f"Storing results in {aggregated_result_file}")
    with open(aggregated_result_file, "w") as f:
        f.write("dataset,distance,runtime,ARI,WHS\n")

    for dataset in tqdm(datasets):
        for distance in distances:
            try:
                h, runtime, ari = run_jet(
                    dataset=dataset,
                    distance=distance,
                    n_jobs=n_jobs,
                    data_folder=data_folder,
                )

                np.savetxt(
                    RESULT_FOLDER
                    / "hierarchies"
                    / f"hierarchy-{dataset}-{distance}.csv",
                    h,
                    delimiter=",",
                )
            except Exception as e:
                print(f"Error for {dataset} with {distance}: {e}")
                runtime = np.nan
                ari = np.nan

            whs = compute_whs(dataset, distance, data_folder)

            with open(aggregated_result_file, "a") as f:
                f.write(f"{dataset},{distance},{runtime},{ari},{whs}\n")


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    main(args.datafolder if args.datafolder else DATA_FOLDER)
