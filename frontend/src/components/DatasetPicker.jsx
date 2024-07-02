import React from "react";
import {SearchSelect, SearchSelectItem} from "@tremor/react";

function DatasetPicker() {
  const data = [
    {
      id: "ts1",
      label: "AllGestureWiimoteY",
      path: "/datasets/ts1.ts",
    },
    {
      id: "ts2",
      label: "AllGestureWiimoteX",
      path: "/datasets/ts2.ts",
    },
    {
      id: "ts3",
      label: "AllGestureWiimoteZ",
      path: "/datasets/ts3.ts",
    },
    {
      id: "ts4",
      label: "BeetleFly",
      path: "/datasets/ts4.ts",
    },
  ]

  return (
    <SearchSelect placeholder="Select dataset" enableClear={true}>
      {data.map((item) => (
        <SearchSelectItem key={item.id} value={item.id}>
          {item.label}
        </SearchSelectItem>
      ))}
    </SearchSelect>
  );
}

export default DatasetPicker;
