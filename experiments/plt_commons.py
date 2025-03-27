# common configuration for plotting code
from collections import defaultdict
import matplotlib

cm = matplotlib.colormaps["inferno"].resampled(9)
colors = defaultdict(lambda: "black")
# strategies
colors["fcfs"] = "gray"
colors["preClustering"] = cm(2)
colors["pre_clustering"] = cm(2)
colors["precl"] = cm(2)
colors["approxAscending"] = cm(3)
colors["approx_distance_ascending"] = cm(3)
colors["ada"] = cm(3)
colors["approxDescending"] = cm(4)
colors["approxFullError"] = cm(5)
colors["shortestTs"] = cm(6)
colors["serial"] = cm(0)
colors["parallel"] = cm(1)
colors["JET"] = "blue"
# measures
colors["hierarchy-quality"] = cm(2)
colors["cluster-quality"] = cm(4)
colors["hierarchy-similarity"] = cm(6)
# phases
colors["Initializing"] = cm(3)
colors["Approximating"] = cm(4)
colors["ComputingFullDistances"] = cm(5)
colors["Finalizing"] = cm(6)


markers = defaultdict(lambda: ".")
markers["serial"] = "o"
markers["parallel"] = "s"
markers["JET"] = "D"
markers["fcfs"] = "X"
markers["preClustering"] = "P"
markers["pre_clustering"] = "P"
markers["approxAscending"] = "^"
markers["approx_distance_ascending"] = "^"
markers["shortestTs"] = "*"


def strategy_name(name):
    if name == "shortestTs":
        return "tsla"
    if name == "fcfs":
        return "fcfs"
    if name in ("approxAscending", "approx_distance_ascending"):
        return "ada"
    if name in ("preClustering", "pre_clustering"):
        return "precl"
    return name


def dataset_name(name):
    if name == "PickupGestureWiimoteZ":
        return "PGWZ"
    if name == "ShakeGestureWiimoteZ":
        return "SGWZ"
    if name == "UWaveGestureLibraryAll":
        return "UWGLA"
    return name


def phase_name(name):
    if name == "Approximating":
        return "(1) Approx."
    if name == "ComputingFullDistances":
        return "(2) Exact"
    if name == "Finalizing":
        return "Final dendrogram"
    return name


distance_name_mapping = {
    "euclidean": "Euclidean",
    "dtw": "\\gls{dtw}",
    "msm": "\\gls{msm}",
    "sbd": "\\gls{sbd}",
}
measure_name_mapping = {
    "ari": "ARI",
    "ariAt": "ARI@k",
    "averageAri": "Average ARI",
    "approxAverageAri": "Approx. average ARI",
    "hierarchySimilarity": "HierarchySimilarity",
    "hierarchyQuality": "WHS",
    "weightedHierarchySimilarity": "WHS",
    "labelChangesAt": "#CumulativeClusterChanges@k",
}
baseline_strategies = ["serial", "parallel", "JET"]
dendrotime_strategies = ["fcfs", "approx_distance_ascending", "pre_clustering"]


def extract_measures_from_config(config_file):
    import json

    with config_file.open("r") as fh:
        config = json.load(fh)
    obj = config["dendrotime"]["progress-indicators"]

    mapping = {}
    for name in ["hierarchy-similarity", "hierarchy-quality", "cluster-quality"]:
        mapped_name = obj[name]
        if mapped_name:
            mapping[name] = mapped_name
    return mapping
