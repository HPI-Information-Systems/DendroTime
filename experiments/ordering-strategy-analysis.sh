#!/usr/bin/env bash
# requires:
# - python with aeon
# - java
# - test-strategies.jar (built with scala-cli --power package --assembly scripts/test-strategies.sc)
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
  java -jar test-strategies.jar "${dataset}" --resultFolder ordering-strategy-analysis/ --dataFolder data/datasets/ --qualityMeasure hierarchy
done
for dataset in "${variable_datasets[@]}"; do
  java -jar test-strategies.jar "${dataset}" --resultFolder ordering-strategy-analysis/ --dataFolder data/datasets/ --qualityMeasure hierarchy
done
