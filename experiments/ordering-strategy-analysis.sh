#!/usr/bin/env bash
# requires:
# - python with aeon
# - java
# - test-strategies.jar (built with scala-cli --power package --assembly scripts/test-strategies.sc)
set -eu

# Run the experiments for the ordering strategy analysis
variable_datasets=(
  "AllGestureWiimoteX"
  "AllGestureWiimoteY"
  "AllGestureWiimoteZ"
  "GestureMidAirD1"
  "GestureMidAirD2"
  "GestureMidAirD3"
  "GesturePebbleZ1"
  "GesturePebbleZ2"
  "PLAID"
  "PickupGestureWiimoteZ"
  "ShakeGestureWiimoteZ"
)

equal_datasets=(
  "Coffee"
  "BeetleFly"
  "Beef"
  "Meat"
  "BirdChicken"
  "BasicMotions"
  "ItalyPowerDemand"
  "Wine"
  "Worms"
  "HouseTwenty"
  "Lightning2"
  "Lightning7"
)

mkdir -p ordering-strategy-analysis

echo "Downloading datasets ..."
for dataset in "${equal_datasets[@]}"; do
  echo "  ${dataset}"
  python -c "from aeon.datasets import load_classification; load_classification('${dataset}', extract_path='data/datasets/')"
done
echo "... done."

echo ""
echo "Processing datasets:"
for dataset in "${variable_datasets[@]}"; do
  java -jar test-strategies.jar "${dataset}" --resultFolder ordering-strategy-analysis/ --dataFolder data/datasets/
done
