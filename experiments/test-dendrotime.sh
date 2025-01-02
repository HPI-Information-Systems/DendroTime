#!/usr/bin/env bash
# requires:
# - python with aeon
# - java
# - DendroTime-runner.jar (built with sbt runner/assembly)
set -eu

# Run the experiments for the ordering strategy analysis
variable_datasets=(
  "PickupGestureWiimoteZ"
  "ShakeGestureWiimoteZ"
#  "GesturePebbleZ1"  # from here on > 42h
#  "GesturePebbleZ2"
#  "GestureMidAirD1"
#  "GestureMidAirD2"
#  "GestureMidAirD3"
#  "AllGestureWiimoteX"
#  "AllGestureWiimoteY"
#  "AllGestureWiimoteZ"
#  "PLAID"
)

equal_datasets=(
  "BirdChicken"
  "BeetleFly"
  "Coffee"
  "Beef"
  "Wine"
  "Meat"
  "Lightning2"
  "Lightning7"
#  "Worms"  # from here on > 22h
#  "HouseTwenty"
#  "ItalyPowerDemand"
)

echo "Downloading datasets ..."
for dataset in "${equal_datasets[@]}"; do
  echo "  ${dataset}"
  python -c "from aeon.datasets import load_classification; load_classification('${dataset}', extract_path='data/datasets/')"
done
for dataset in "${variable_datasets[@]}"; do
  echo "  ${dataset}"
  python -c "from aeon.datasets import load_classification; load_classification('${dataset}', extract_path='data/datasets/')"
done
echo "... done."

echo ""
echo "Processing datasets:"
for dataset in "${equal_datasets[@]}"; do
  ./run.sh --dataset "${dataset}"
done
for dataset in "${variable_datasets[@]}"; do
  ./run.sh --dataset "${dataset}"
done
