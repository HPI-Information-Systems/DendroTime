#!/usr/bin/env bash
# This script runs a command on all (small) datasets.
# Usage: forall-datasets.sh command
set -eu

# Check that a command was given
if [ $# -eq 0 ]; then
    echo "Usage: $0 command"
    exit 1
fi

datasets=(
  "BirdChicken"
  "BeetleFly"
  "Coffee"
  "Beef"
  "Wine"
  "Meat"
  "Lightning2"
  "Lightning7"
  "PickupGestureWiimoteZ"
  "ShakeGestureWiimoteZ"
#  "Worms"  # from here on > 22h
#  "HouseTwenty"
#  "ItalyPowerDemand"
#  "GesturePebbleZ1"
#  "GesturePebbleZ2"
#  "GestureMidAirD1"
#  "GestureMidAirD2"
#  "GestureMidAirD3"
#  "AllGestureWiimoteX"
#  "AllGestureWiimoteY"
#  "AllGestureWiimoteZ"
#  "PLAID"
)

# Run the command on each dataset
for dataset in "${datasets[@]}"; do
    echo "Running $1 on $dataset"
    eval "$1 $dataset"
done
