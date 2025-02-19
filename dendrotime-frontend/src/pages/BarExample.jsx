import D3BarChart from "../components/D3BarChart";
import {Button, Divider} from "@tremor/react";
import React, {useState, useEffect, useCallback} from "react";
import DatasetPicker from "../components/DatasetPicker";
import StatusOverview from "../components/StatusOverview";
import {toast} from "react-toastify";
import D3Dendrogram from "../components/D3Dendrogram";
import WidthProvider from "../components/WidthProvider";

function BarExample() {
  const [data, setData] = useState([12, 5, 6, 6, 9, 10]);
  const [dataset, setDataset] = useState(undefined);

  const addData = () => {
    setData(d => d.concat([Math.trunc(Math.random()*50)]));
  };
  const removeData = () => {
    setData(d => d.length > 1 ? d.slice(0, -1) : d);
  };
  const changeData = () => {
    setData(d => {
      const newD = [...d];
      newD[Math.trunc(Math.random()*newD.length)] += 10;
      return newD;
    })
  };

  return (
    <div className="m-5">
      <h1 className="text-3xl font-bold">Bar Char Demo</h1>
      <D3BarChart data={data}/>
      <div className="flex flex-auto m-auto mx-auto justify-between items-center">
        <Button className="mx-auto" variant="primary" size="md" onClick={addData}>
          Add
        </Button>
        <Button className="mx-auto" variant="primary" size="md" onClick={removeData}>
          Remove
        </Button>
        <Button className="mx-auto" variant="secondary" size="md" onClick={changeData}>
          +10
        </Button>
      </div>
    </div>
  )
}

export default BarExample;
