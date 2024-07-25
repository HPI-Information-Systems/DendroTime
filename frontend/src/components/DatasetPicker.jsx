import React, {useEffect, useState} from "react";
import {SearchSelect, SearchSelectItem} from "@tremor/react";

function DatasetPicker({ onSelect }) {
  const [datasets, setDatasets] = useState([]);
  useEffect(() => {
    fetch("/api/datasets", {method: "GET", headers: {'Content-Type': 'application/json'}, mode: 'no-cors'})
      .then((res) => res.json())
      .catch((error) => {
        console.error("Error fetching datasets:", error);
        return [];
      })
      .then((data) => setDatasets(data.datasets));
  }, []);

  return (
    <SearchSelect placeholder="Select dataset" enableClear={true}>
      {datasets.length > 0 ? datasets.map((item) => (
        <SearchSelectItem key={item.id} value={item.id} onClick={e => {e.stopPropagation(); onSelect(item)}}>
          {item.name}
        </SearchSelectItem>
      )) : (
        <SearchSelectItem key={undefined} value={undefined} aria-disabled={true} disabled={true} className="text-gray-500">
          No datasets available.
        </SearchSelectItem>
      )}
    </SearchSelect>
  );
}

export default DatasetPicker;
