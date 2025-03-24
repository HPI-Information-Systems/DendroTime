#!/usr/bin/env bash

set -eo pipefail  # trace exit code of failed piped commands

# prepare result directories
mkdir -p figures
mkdir -p tables

# 01-serial-hac: create runtime ratio table
echo ""
echo "Creating runtime ratio table for 01-serial-hac"
pushd 01-serial-hac
python create-runtimes-ratio-table.py
popd
mv 01-serial-hac/runtimes-ratio-table.tex tables/

# 02-strategy-analysis: create plots just for some configurations
# skip because I have not downloaded all results
# echo ""
# echo "Creating strategy WHS-R-AUC plots (MSM/SBD and average/ward) for 02-strategy-analysis"
# pushd 02-strategy-analysis
# # - msm with ward-linkage
# python create-strategy-qualities-plot.py results/msm-ward-weightedHierarchySimilarity --boxplot
# # - msm with average-linkage
# python create-strategy-qualities-plot.py results/msm-average-weightedHierarchySimilarity --boxplot
# # - sbd with average-linkage
# python create-strategy-qualities-plot.py results/sbd-average-weightedHierarchySimilarity --boxplot
# # - dtw with average-linkage
# python create-strategy-qualities-plot.py results/dtw-average-weightedHierarchySimilarity --boxplot
# popd
# mv 02-strategy-analysis/*.pdf figures/

# 03-approx-dissimilarities-segmentation
echo ""
echo "Creating dissimilarities distribution plot (on BeetleFly with DTW) for 03-approx-dissimilarities-segmentation"
pushd 03-approx-dissimilarities-segmentation
python create-dissimilarities-distribution-plot.py
popd
mv 03-approx-dissimilarities-segmentation/BeetleFly_dtw_dists_distribution.pdf figures/

# 04-dendrotime
echo ""
echo "Creating convergence indicator vs WHS plots (ada and fcfs on ACSF1) for 04-dendrotime"
pushd 04-dendrotime
python plot-qualities.py --dataset ACSF1 --use-runtime
python plot-qualities.py --dataset ACSF1 --use-runtime --strategy fcfs
popd
mv 04-dendrotime/solutions-ACSF1-*.pdf figures/

# 05-whsim-example
echo ""
echo "Creating WHS over runtime plots (on ACSF1 for MSM average and DTW single; ada and fcfs) for 05-whsim-example"
pushd 05-whsim-example
# find results -iname "qualities.csv" -exec python create-whsim-plot.py --resultfile {} \;
python create-whsim-plot.py --dataset ACSF1 --distance msm --linkage average --strategy approx-distance-ascending
python create-whsim-plot.py --dataset ACSF1 --distance msm --linkage average --strategy fcfs
python create-whsim-plot.py --dataset ACSF1 --distance dtw --linkage single --strategy approx-distance-ascending
python create-whsim-plot.py --dataset ACSF1 --distance dtw --linkage single --strategy fcfs
popd
mv 05-whsim-example/whsim-ACSF1-*.pdf figures/

# global plots: runtime vs quality
echo ""
echo "Creating global runtime vs quality plot"
python create-runtime-plot.py --disable-variances -c
mv mean-runtime-qualities.pdf figures/

# global plots: convergence table
echo ""
echo "Creating convergence table"
python create-quality-table.py --exclude-jet
mv quality-table.tex tables/

# global plots: comparison plots
echo ""
echo "Creating comparison plot for worst-case datasets"
python create-comparison-plot.py Crop-dtw-weighted ElectricDevices-dtw-weighted --highlight-phase Approximating --include-baselines -c
echo ""
echo "Creating comparison plot for efficient dissimilarity"
python create-comparison-plot.py UWaveGestureLibraryAll-msm-average UWaveGestureLibraryAll-euclidean-average --highlight-phase ComputingFullDistances -c
mv comparison_*.pdf figures/
