import D3BarChart from "../components/D3BarChart";
import {Button} from "@tremor/react";
import React, {useState} from "react";
import DatasetPicker from "../components/DatasetPicker";
import StatusOverview from "../components/StatusOverview";

function ClusteringPage() {
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
  const progress = data.length * 10;

  const startDemoJob = () => {
    console.log("Fetching dataset", dataset);
    fetch("/api/jobs", {
      method: "POST",
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(dataset)
    })
      .then(console.log)
      .catch(console.error);
  }

  return (
    <div className="m-5">
      <h1 className="text-3xl font-bold">Clustering</h1>
      <div className="flex flex-auto mx-auto">
        <DatasetPicker onSelect={dataset => setDataset(dataset)}/>
      </div>
      <StatusOverview jobId={0} progressPlaceholder={progress}/>
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
      <div className="flex justify-center items-center m-10">
        <Button className="mx-auto" variant="primary" size="lg" onClick={startDemoJob}>
          Start demo Job
        </Button>
      </div>
      <br/>
    </div>
  )
}

export default ClusteringPage;
