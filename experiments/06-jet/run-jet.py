#!/usr/bin/env python
import sys
import argparse
import psutil

import numpy as np

from pathlib import Path
from aeon.utils.validation import check_n_jobs

sys.path.append(str(Path(__file__).resolve().parent.parent))
from download_datasets import DATA_FOLDER

from jet_wrapper import distance_functions, run_jet

RESULT_FOLDER = Path("results")
DEFAULT_N_JOBS = check_n_jobs(psutil.cpu_count(logical=False))


def parse_args(args):
    parser = argparse.ArgumentParser(description="Execute JET.")
    parser.add_argument(
        "--datafolder",
        type=str,
        help="Overwrite the folder, where the datasets are stored",
    )
    parser.add_argument(
        "--dataset",
        type=str,
        help="Target dataset",
    )
    parser.add_argument(
        "--distance",
        type=str,
        default="sbd",
        choices=list(distance_functions.keys()),
        help="Distance function to use",
    )
    parser.add_argument(
        "--n_jobs",
        type=int,
        default=DEFAULT_N_JOBS,
        help="Number of jobs to use for parallel processing",
    )
    return parser.parse_args(args)


def main(data_folder, dataset, distance, n_jobs):
    print(f"Using {n_jobs} jobs")

    try:
        h, runtime, ari = run_jet(
            data_folder,
            dataset,
            distance=distance,
            n_jobs=n_jobs,
        )
        print(
            f"JET took {runtime:.2f} seconds to process {dataset} with {distance}: "
            f"{ari=:.2f}"
        )

        print(f"Storing hierarchy at {result_path}")
        np.savetxt(result_path, h, delimiter=",")
    except Exception as e:
        print(f"Error for {dataset} with {distance}: {e}")
        runtime = np.nan


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    data_folder = args.datafolder if args.datafolder else DATA_FOLDER
    dataset = args.dataset
    distance = args.distance
    n_jobs = args.n_jobs

    result_path = RESULT_FOLDER / "hierarchies" / f"hierarchy-{dataset}-{distance}.csv"
    result_path.parent.mkdir(exist_ok=True, parents=True)

    main(data_folder, dataset, distance, n_jobs)
