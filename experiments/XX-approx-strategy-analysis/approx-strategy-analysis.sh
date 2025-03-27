#!/usr/bin/env bash
# requires:
# - python with aeon
# - java
# - test-approximations.jar (built with scala-cli --power package --assembly scripts/test-approximations.sc)
set -eu

# Run the experiments for the ordering strategy analysis
variable_datasets=(
  "PickupGestureWiimoteZ"
  "ShakeGestureWiimoteZ"
  "GesturePebbleZ1"
  "GesturePebbleZ2"
  "GestureMidAirD1"
  "GestureMidAirD2"
  "GestureMidAirD3"
  "AllGestureWiimoteX"
  "AllGestureWiimoteY"
  "AllGestureWiimoteZ"
  "PLAID"
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
  "Worms"
  "HouseTwenty"
  "ItalyPowerDemand"
)

mkdir -p approx-strategy-analysis

echo "Downloading datasets ..."
for dataset in "${equal_datasets[@]}"; do
  echo "  ${dataset}"
  python -c "from aeon.datasets import load_classification; load_classification('${dataset}', extract_path='../data/datasets/')"
done
for dataset in "${variable_datasets[@]}"; do
  echo "  ${dataset}"
  python -c "from aeon.datasets import load_classification; load_classification('${dataset}', extract_path='../data/datasets/')"
done
echo "... done."

echo ""
echo "Processing equal length datasets:"
for dataset in "${equal_datasets[@]}"; do
  java -jar test-approximations.jar "${dataset}" --resultFolder approx-strategy-analysis/ --dataFolder ../data/datasets/ --all true
done

echo ""
echo "Processing variable length datasets:"
for dataset in "${variable_datasets[@]}"; do
  java -jar test-approximations.jar "${dataset}" --resultFolder approx-strategy-analysis/ --dataFolder ../data/datasets/ --all true
done
