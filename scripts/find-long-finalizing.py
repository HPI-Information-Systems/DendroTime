import pandas as pd

from pathlib import Path

RESULT_PATH = Path("../experiments/04-dendrotime/results")


def main():
    threshold = 0.2
    selected_strategy = "approx_distance_ascending"
    hac_ratios = []
    for exp_folder in RESULT_PATH.iterdir():
        if not exp_folder.is_dir():
            continue
        (dataset, distance, linkage, strategy) = exp_folder.name.split("-")
        if strategy == selected_strategy and distance != "euclidean":
            df = pd.read_csv(exp_folder / "Finished-100" / "runtimes.csv")
            df = df.set_index("phase")
            ratio = df.loc["Finalizing", "runtime"] / df.loc["Finished", "runtime"]
            hac_ratios.append({
                "dataset": dataset,
                "distance": distance,
                "linkage": linkage,
                "ratio": ratio
            })
    df = pd.DataFrame(hac_ratios)
    df = df[df["ratio"] > threshold]
    df = df.groupby(["distance", "linkage"])[["dataset"]].count()
    # df = df.sort_values("ratio", ascending=False)
    print(df)


if __name__ == "__main__":
    main()
