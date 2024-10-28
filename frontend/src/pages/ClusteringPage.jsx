import {Button, Divider, Select, SelectItem, Switch} from "@tremor/react";
import React, {useState, useEffect, useCallback} from "react";
import DatasetPicker from "../components/DatasetPicker";
import StatusOverview from "../components/StatusOverview";
import {toast} from "react-toastify";
import D3Dendrogram from "../components/D3Dendrogram";
import WidthProvider from "../components/WidthProvider";
import D3LineChart from "../components/D3LineChart";
import {Label} from "@headlessui/react";

const defaultState = {
  "hierarchy": {
    "hierarchy": [],
    "n": 0
  },
  "state": "Initializing",
  "progress": 0,
  "hierarchySimilarity": [],
  "hierarchyQuality": [],
  "clusterQuality": []
}

function ClusteringPage() {
  const [dataset, setDataset] = useState(undefined);
  const [metric, setMetric] = useState("msm");
  const [linkage, setLinkage] = useState("ward");
  const [state, setState] = useState(defaultState);
  const [jobId, setJobId] = useState(undefined);
  const [polling, setPolling] = useState(null);
  const [useEqualNodeDistance, setUseEqualNodeDistance] = useState(false);
  const pollingInterval = 200;

  const startJob = useCallback(() => {
    fetch("/api/jobs", {
      method: "POST",
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        "dataset": dataset,
        "params": {
          "metricName": metric,
          "linkageName": linkage,
          "approxLength": 10,
        }
      })
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
  }, [dataset, metric, linkage, setJobId, setState]);

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
      <div className="flex flex-auto items-center my-1">
        <label htmlFor="dataset-picker" className="mr-2">Dataset:</label>
        <DatasetPicker id="dataset-picker" onSelect={dataset => setDataset(dataset)}/>
      </div>
      <div className="flex flex-nowrap items-center my-1">
        <label htmlFor="metric-picker" className="mr-2">Metric:</label>
        <Select id="metric-picker" value={metric} onValueChange={setMetric}>
          <SelectItem key="msm" value="msm">MSM</SelectItem>
          <SelectItem key="sbd" value="sbd">SBD</SelectItem>
        </Select>
        <label htmlFor="linkage-picker" className="ml-2 mr-2">Linkage:</label>
        <Select id="linkage-picker" value={linkage} onValueChange={setLinkage}>
          <SelectItem key="single" value="single">Single Linkage</SelectItem>
          <SelectItem key="complete" value="complete">Complete Linkage</SelectItem>
          <SelectItem key="ward" value="ward">Ward Linkage</SelectItem>
          <SelectItem key="average" value="average">Average Linkage</SelectItem>
        </Select>
      </div>
      <StatusOverview key={jobId} jobId={jobId} progress={state.progress} activeState={state.state}/>
      <div className="flex justify-center items-center mt-5">
        <Button className="mx-auto" variant="primary" size="lg" disabled={!!polling || !dataset} onClick={startJob}>
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
          {state.hierarchySimilarity.length === 0 ? (<></>) : (<>
              <h3 className="text-tremor-title text-tremor-emphasis dark:text-dark-termor-emphasis mt-2 mb-1 mx-auto">
                Changes over Time
              </h3>
              <D3LineChart data={{
                "hierarchySimilarity": state.hierarchySimilarity,
                "hierarchyQuality": state.hierarchyQuality,
                "clusterQuality": state.clusterQuality
              }}/>
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
