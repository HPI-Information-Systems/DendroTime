import {Button, Divider, Select, SelectItem, Switch} from "@tremor/react";
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
  "steps": [],
  "timestamps": [],
  "hierarchySimilarities": [],
  "hierarchyQualities": [],
  "clusterQualities": [],
}

function ClusteringPage() {
  const [dataset, setDataset] = useState(undefined);
  const [metric, setMetric] = useState("msm");
  const [linkage, setLinkage] = useState("average");
  const [strategy, setStrategy] = useState("approx-distance-ascending");
  const [state, setState] = useState(defaultState);
  const [jobId, setJobId] = useState(undefined);
  const [polling, setPolling] = useState(null);
  const [useEqualNodeDistance, setUseEqualNodeDistance] = useState(false);
  const [useTimestamps, setUseTimestamps] = useState(true);
  const [forceDendrogramDisplay, setForceDendrogramDisplay] = useState(false);
  const pollingInterval = 200;

  const startJob = useCallback(() => {
    fetch("/api/jobs", {
      method: "POST",
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        "dataset": dataset,
        "params": {
          "distanceName": metric,
          "linkageName": linkage,
          "strategy": strategy
        },
      })
    })
      .then(resp => {
        if (!resp.ok) {
          throw new Error("Failed to start job: " + resp.statusText);
        }
        return resp.json();
      })
      .then(data => {
        if (!data) return;
        const jobId = data.id;
        setJobId(jobId);
        setState(defaultState);
        startPolling(jobId);
      })
      .catch(toast.error);
  }, [dataset, metric, linkage, strategy, setJobId, setState]);

  const startPolling = useCallback((jobId) => {
    toast.info("Starting job " + jobId + " and polling ...");
    const intervalRef = {};
    function poll() {
      fetch("/api/jobs/" + jobId + "/progress")
        .then(resp => {
          if (resp.status === 200) {
            resp.json().then(data => setState(data));
          } else if (resp.status >= 300) {
            throw new Error("Failed to poll job " + jobId + ": " + resp.statusText);
          }
          // ignore other responses
        })
        .catch(msg => {
          toast.error("ERROR: " + msg);
          clearInterval(intervalRef.inter);
          setPolling(null);
        });
    }
    const inter = setInterval(poll, pollingInterval);
    intervalRef.inter = inter;
    setPolling(inter);
    poll();
  }, [setState, setPolling]);

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
        if (resp.status >= 300)
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

  const handleTimestampSwitch = useCallback(() => {
    setUseTimestamps(!useTimestamps);
  }, [useTimestamps, setUseTimestamps]);

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
          <SelectItem key="dtw" value="dtw">DTW</SelectItem>
          <SelectItem key="euclidean" value="euclidean">Euclidean</SelectItem>
          <SelectItem key="manhatten" value="manhatten">Manhatten</SelectItem>
        </Select>
        <label htmlFor="linkage-picker" className="ml-2 mr-2">Linkage:</label>
        <Select id="linkage-picker" value={linkage} onValueChange={setLinkage}>
          <SelectItem key="single" value="single">Single Linkage</SelectItem>
          <SelectItem key="complete" value="complete">Complete Linkage</SelectItem>
          <SelectItem key="average" value="average">Average Linkage</SelectItem>
          <SelectItem key="weighted" value="weighted">Weighted Linkage</SelectItem>
          <SelectItem key="ward" value="ward">Ward Linkage</SelectItem>
          <SelectItem key="centroid" value="centroid">Centroid Linkage</SelectItem>
          {/*<SelectItem key="median" value="median">Median Linkage</SelectItem>*/}
        </Select>
        <label htmlFor="strategy-picker" className="ml-2 mr-2">Strategy:</label>
        <Select id="strategy-picker" value={strategy} onValueChange={setStrategy}>
          <SelectItem key="fcfs" value="fcfs">FCFS</SelectItem>
          <SelectItem key="shortest-ts" value="shortest-ts">Shortest TS First</SelectItem>
          <SelectItem key="approx-distance-ascending" value="approx-distance-ascending">Approx. Distance Ascending</SelectItem>
          <SelectItem key="approx-distance-descending" value="approx-distance-descending">Approx. Distance Descending</SelectItem>
          <SelectItem key="pre-clustering" value="pre-clustering">PreClustering</SelectItem>
        </Select>
      </div>
      <div className="flex justify-center items-center mt-3 mb-5">
        <Button className="mx-auto" variant="primary" size="lg" disabled={!!polling || !dataset} onClick={startJob}>
          Start Clustering
        </Button>
        <Button className="mx-auto" variant="secondary" disabled={!polling} size="lg" onClick={abortPolling}>
          Cancel Clustering
        </Button>
        <div className="grid-cols-1 mx-auto">
          <div className="flex items-center space-x-3 my-2">
            <Switch id="horiz-measure-switch" checked={useEqualNodeDistance} onChange={handleDistanceMetricSwitch}/>
            <label htmlFor="horiz-measure-switch"
                   className="text-tremor-default text-tremor-content dark:text-dark-termor-content">
              Use equal node distances
            </label>
          </div>
          <div className="flex items-center space-x-3 mb-2">
            <Switch id="timestamp-switch" checked={useTimestamps} onChange={handleTimestampSwitch}/>
            <label htmlFor="timestamp-switch"
                    className="text-tremor-default text-tremor-content dark:text-dark-termor-content">
                Use timestamps
            </label>
          </div>
        </div>
      </div>
      <StatusOverview key={jobId} jobId={jobId} progress={state.progress} activeState={state.state}/>
      <Divider/>
      <WidthProvider>
        <div className={"grid grid-cols-1 my-auto"}>
          {state.hierarchySimilarities.length === 0 ? (<></>) : (<>
              <h2 className="text-tremor-title text-tremor-emphasis dark:text-dark-termor-emphasis mb-1 mx-auto">
                Convergence
              </h2>
              <D3LineChart data={{
                "steps": state.steps,
                "timestamps": state.timestamps,
                "hierarchySimilarities": state.hierarchySimilarities,
                "hierarchyQualities": state.hierarchyQualities,
                "clusterQualities": state.clusterQualities
              }} useTimestamps={useTimestamps}/>
          </>)}
          {!state.hierarchy.hierarchy || state.hierarchy.hierarchy.length === 0 ? (<></>) : (<>
            <h2 className="text-tremor-title text-tremor-emphasis dark:text-dark-termor-emphasis mt-4 mb-1 mx-auto">
              Dendrogram
            </h2>
            {state.hierarchy.n > 300 ? (<>
              <div className="flex justify-center items-center space-x-3 my-2">
                <span className="text-tremor-default text-tremor-content dark:text-dark-termor-content">
                  The dendrogram is too large to display ({state.hierarchy.n} time series).
                </span>
                <Switch id="force-dendrogram-switch" checked={forceDendrogramDisplay} onChange={setForceDendrogramDisplay}/>
                <label htmlFor="force-dendrogram-switch"
                       className="text-tremor-default text-tremor-content dark:text-dark-termor-content">
                  Display anyway
                </label>
              </div>
              {forceDendrogramDisplay? (
                  <D3Dendrogram key={jobId} data={state.hierarchy} useEqualNodeDistance={useEqualNodeDistance}/>
                ) : (<></>)}
              </>
            ) : (<D3Dendrogram key={jobId} data={state.hierarchy} useEqualNodeDistance={useEqualNodeDistance}/>)}
          </>)}
          <div className="flex justify-center items-center my-1 mx-auto">
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
        </div>
      </WidthProvider>
    </div>
  )
}

export default ClusteringPage;
