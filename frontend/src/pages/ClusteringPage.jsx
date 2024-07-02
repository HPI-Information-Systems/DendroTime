import D3BarChart from "../components/D3BarChart";
import {Button, ProgressBar} from "@tremor/react";
import React, {useState} from "react";
import DatasetPicker from "../components/DatasetPicker";

function ClusteringPage() {
  const [data, setData] = useState([12, 5, 6, 6, 9, 10]);

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
  const progress = data.length * 10;

  return (
    <div>
      <h1 className="text-3xl font-bold">Clustering</h1>
      <div className="flex flex-auto mx-auto">
        <DatasetPicker/>
      </div>
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
      <br/>
      <div className="flex flex-auto mx-auto">
        Progress: <ProgressBar value={progress} label={progress.toString() + "%"} className="ml-2" />
      </div>
    </div>
  )
}

export default ClusteringPage;
