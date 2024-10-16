import {Button, Divider, Switch} from "@tremor/react";
import React, {useState, useEffect, useCallback} from "react";
import DatasetPicker from "../components/DatasetPicker";
import StatusOverview from "../components/StatusOverview";
import {toast} from "react-toastify";
import D3Dendrogram from "../components/D3Dendrogram";
import WidthProvider from "../components/WidthProvider";
import D3LineChart from "../components/D3LineChart";

const defaultState = {
  "hierarchy": {
    "hierarchy": [],
    "n": 0
  },
  "state": "Initializing",
  "progress": 0,
  "similarities": []
}

function ClusteringPage() {
  const [dataset, setDataset] = useState(undefined);
  const [state, setState] = useState(defaultState);
  const [jobId, setJobId] = useState(undefined);
  const [polling, setPolling] = useState(null);
  const [useEqualNodeDistance, setUseEqualNodeDistance] = useState(false);
  const pollingInterval = 200;

  const startDemoJob = useCallback(() => {
    fetch("/api/jobs", {
      method: "POST",
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(dataset)
    })
      .then(resp => {
        if (resp.status >= 300) {
          resp.text().then(txt => toast.error("Failed to start job: " + txt));
        } else {
          return resp.json();
        }
      })
      .then(data => {
        if (!data) return;
        const jobId = data.id;
        setJobId(jobId);
        setState(defaultState);
        startPolling(jobId);
      })
      .catch(toast.error);
  }, [dataset, setJobId, setState]);

  const startPolling = useCallback((jobId) => {
    toast.info("Starting job " + jobId + " and polling ...");
    function poll() {
      fetch("/api/jobs/" + jobId + "/progress")
        .then(resp => resp.json())
        .then(data => setState(data))
        .catch(toast.error);
    }
    const inter = setInterval(poll, pollingInterval);
    setPolling(inter);
    poll();
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
    if (polling && state.state === "Finished") {
      clearInterval(polling);
      setPolling(null);
      toast.success("Job " + jobId + " finished successfully!");
      setTimeout(() => fetch("/api/jobs/" + jobId, {
        method: "POST",
        headers: {'Content-Type': 'application/json'},
        body: "stop"
      }).catch(toast.error), 500);

    }
  }, [polling, state, setPolling]);

  const handleDistanceMetricSwitch = useCallback(() => {
    setUseEqualNodeDistance(!useEqualNodeDistance);
  }, [useEqualNodeDistance, setUseEqualNodeDistance]);

  return (
    <div className="m-5">
      <h1 className="text-3xl font-bold m-5 mx-auto">Clustering</h1>
      <div className="flex flex-auto mx-auto">
        <DatasetPicker onSelect={dataset => setDataset(dataset)}/>
      </div>
      <StatusOverview key={jobId} jobId={jobId} progress={state.progress} activeState={state.state}/>
      <div className="flex justify-center items-center mt-5">
        <Button className="mx-auto" variant="primary" size="lg" disabled={!!polling || !dataset} onClick={startDemoJob}>
          Start Clustering
        </Button>
        <Button className="mx-auto" variant="secondary" disabled={!polling} size="lg" onClick={abortPolling}>
          Cancel Clustering
        </Button>
        <div className="flex items-center space-x-3 m-5 mx-auto">
          <Switch id="horiz-measure-switch" checked={useEqualNodeDistance} onChange={handleDistanceMetricSwitch}/>
          <label htmlFor="horiz-measure-switch"
                 className="text-tremor-default text-tremor-content dark:text-dark-termor-content">
            Use equal node distances
          </label>
        </div>
      </div>
      <WidthProvider>
        <div className="flex justify-center items-center mt-5 mx-auto">
          {(jobId && polling) ? (
            <span className="text-tremor-default text-tremor-content dark:text-dark-termor-content">
              Currently processing job {jobId ? jobId.toString() : ""}: Dataset {dataset.name}.
            </span>
          ) : (jobId && state?.state === "Finished") ? (
            <span className="text-tremor-default text-tremor-content dark:text-dark-termor-content">
              Finished processing job {jobId ? jobId.toString() : ""}.
            </span>
          ) : (<></>)}
        </div>
        <Divider/>
        <div className={"grid grid-cols-1 my-auto"}>
          {state.similarities.length === 0 ? (<></>) : (<>
              <h3 className="text-tremor-title text-tremor-emphasis dark:text-dark-termor-emphasis mt-2 mb-1 mx-auto">
                Changes over Time
              </h3>
              <D3LineChart data={state.similarities}/>
          </>)}
          {!state.hierarchy.hierarchy || state.hierarchy.hierarchy.length === 0 ? (<></>) : (<>
            <h3 className="text-tremor-title text-tremor-emphasis dark:text-dark-termor-emphasis mt-2 mb-1 mx-auto">
              Dendrogram
            </h3>
            <D3Dendrogram key={jobId} data={state.hierarchy} useEqualNodeDistance={useEqualNodeDistance}/>
          </>)}
        </div>
      </WidthProvider>
    </div>
  )
}

export default ClusteringPage;
