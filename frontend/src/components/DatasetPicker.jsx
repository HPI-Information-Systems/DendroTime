import React, {useEffect, useState} from "react";
import {SearchSelect, SearchSelectItem} from "@tremor/react";

function DatasetPicker({ onSelect }) {
  const [datasets, setDatasets] = useState([]);
  useEffect(() => {
    fetch("/api/datasets", {method: "GET", headers: {'Content-Type': 'application/json'}, mode: 'no-cors'})
      .then((res) => res.json())
      .then((data) => setDatasets(data.datasets));
  }, []);

  return (
    <SearchSelect placeholder="Select dataset" enableClear={true}>
      {datasets.map((item) => (
        <SearchSelectItem key={item.id} value={item.id} onClick={e => {e.stopPropagation(); onSelect(item)}}>
          {item.name}
        </SearchSelectItem>
      ))}
    </SearchSelect>
  );
}

export default DatasetPicker;
