#!/usr/bin/env python3
import pandas as pd
import matplotlib.pyplot as plt


def main():
    df = pd.read_csv("results/aggregated-runtimes.csv")
    df = df[(df["phase"] == "ComputingFullDistances") & (df["linkage"] == "average")]
    df["runtime"] = df["runtime"] / 1000  # convert to seconds
    df = df.drop(columns=["phase", "strategy", "linkage"])
    df = df.pivot(index="dataset", columns="distance", values="runtime")
    distances = list(sorted(df.columns))
    df["sbd-vs-msm"] = df["msm"] / df["sbd"]
    df["sbd-vs-dtw"] = df["dtw"] / df["sbd"]
    print(df)

    mean_sp_vs_msm = df["sbd-vs-msm"].mean()
    mean_sp_vs_dtw = df["sbd-vs-dtw"].mean()
    median_sp_vs_msm = df["sbd-vs-msm"].median()
    median_sp_vs_dtw = df["sbd-vs-dtw"].median()
    print(f"Mean speedup of SBD over MSM: {mean_sp_vs_msm:.2f}")
    print(f"Mean speedup of SBD over DTW: {mean_sp_vs_dtw:.2f}")
    print(f"Median speedup of SBD over MSM: {median_sp_vs_msm:.2f}")
    print(f"Median speedup of SBD over DTW: {median_sp_vs_dtw:.2f}")

    fig, axs = plt.subplots(2, 1, figsize=(10, 7), constrained_layout=True)
    axs[0].set_title("Runtime comparison for different distances")
    for i, distance in enumerate(distances):
        axs[0].boxplot(
            df[distance].values,
            positions=[i],
            tick_labels=[distance],
            widths=0.6,
            whis=(0, 100),
            meanline=True,
            showmeans=True,
            sym="",
        )
    axs[0].set_ylabel("Runtime [s]")
    axs[0].set_yscale("log")
    axs[0].set_ylim(1e-3, 3600 * 24)
    axs[0].set_yticks([1e-3, 1e-1, 1, 60, 3600, 3600 * 24])
    axs[0].set_yticklabels(["1ms", "100ms", "1s", "1m", "1h", "1d"])
    axs[0].yaxis.grid(True, which="major", linestyle=":", lw=1)

    df = df.sort_values("euclidean", ascending=True)
    axs[1].set_title("Speedup of SBD over MSM and DTW")
    axs[1].plot(df["euclidean"], df["sbd-vs-msm"], label="SBD SU vs MSM")
    axs[1].plot(df["euclidean"], df["sbd-vs-dtw"], label="SBD SU vs DTW")
    axs[1].set_xlabel("Runtime of Euclidean distance [s] (linear in dataset size)")
    axs[1].set_ylabel("Speedup")

    handles, _ = axs[1].get_legend_handles_labels()
    labels = [
        f"SBD SU vs MSM:\nmean={mean_sp_vs_msm:.2f}, median={median_sp_vs_msm:.2f}",
        f"SBD SU vs DTW:\nmean={mean_sp_vs_dtw:.2f}, median={median_sp_vs_dtw:.2f}",
    ]
    axs[1].legend(handles, labels, loc="upper right")

    plt.show()


if __name__ == "__main__":
    main()
