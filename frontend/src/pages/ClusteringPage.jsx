import D3BarChart from "../components/D3BarChart";
import {Button, Divider} from "@tremor/react";
import React, {useState, useEffect, useCallback} from "react";
import DatasetPicker from "../components/DatasetPicker";
import StatusOverview from "../components/StatusOverview";
import {toast} from "react-toastify";
import D3Dendrogram from "../components/D3Dendrogram";

const defaultState = {
  "hierarchy": {
    "hierarchy": [],
    "n": 0
  },
  "state": "Initializing",
  "progress": 0
}

function ClusteringPage() {
  const [data, setData] = useState([12, 5, 6, 6, 9, 10]);
  const [dataset, setDataset] = useState(undefined);
  const [state, setState] = useState(defaultState);
  const [jobId, setJobId] = useState(undefined);
  const [polling, setPolling] = useState(null);

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

  const startDemoJob = useCallback(() => {
    fetch("/api/jobs", {
      method: "POST",
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(dataset)
    })
      .then(resp => resp.json())
      .then(data => {
        const jobId = data.id;
        setJobId(jobId);
        startPolling(jobId);
      })
      .catch(toast.error);
  }, [dataset, setJobId]);

  const startPolling = useCallback((jobId) => {
    toast.info("Starting job " + jobId + " and polling ...");
    const inter = setInterval(() => {
      console.debug("Polling...");
      fetch("/api/jobs/" + jobId + "/progress", {
        method: "GET",
        headers: {'Content-Type': 'application/json'},
      })
        .then(resp => resp.json())
        .then(data => setState(data))
        .catch(toast.error);
    }, 2000);
    setPolling(inter);
  }, [setState]);

  const abortPolling = useCallback(() => {
    if (polling) {
      clearInterval(polling);
      setPolling(null);
    }
    fetch("/api/jobs/" + jobId, {
      method: "DELETE",
      headers: {'Content-Type': 'application/json'},
    })
      .then(resp => {
        if (!resp.ok)
          throw "Failed to cancel job " + jobId + ": " + resp.statusText;
        else
          return resp;
      })
      .then(resp => resp.text())
      .then(txt => toast.info("Aborting polling: " + txt))
      .catch(toast.error);
  }, [jobId, polling, setPolling]);

  useEffect(() => {
    if (state.state === "Approximating" && state.progress === 100) {
      toast.warning("DEV: Stopping polling after approximating state is done!");
      abortPolling();
    }
  }, [state]);

  return (
    <div className="m-5">
      <h1 className="text-3xl font-bold">Clustering</h1>
      <div className="flex flex-auto mx-auto">
        <DatasetPicker onSelect={dataset => setDataset(dataset)}/>
      </div>
      <StatusOverview key={jobId} jobId={jobId} progress={state.progress} activeState={state.state}/>
      <div className="flex justify-center items-center m-10">
        <Button className="mx-auto" variant="primary" size="lg" disabled={!!polling || !dataset} onClick={startDemoJob}>
          Start demo Job
        </Button>
        <Button className="mx-auto" variant="secondary" disabled={false && !polling} size="lg" onClick={abortPolling}>
          Abort Job {jobId ? jobId.toString() : ""}
        </Button>
      </div>
      <Divider />
      <D3Dendrogram data={state.hierarchy} />
      <Divider />
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

export default ClusteringPage;
