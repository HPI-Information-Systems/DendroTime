import React, {useCallback, useState} from "react";
import {toast} from "react-toastify";
import D3Dendrogram from "../components/D3Dendrogram";
import {Button, Divider} from "@tremor/react";

const approxData = {
  "hierarchy": {
    "hierarchy": [
      {
        "cId1": 0,
        "cId2": 0,
        "cardinality": 2,
        "distance": 0.0,
        "idx": 0
      },
      {
        "cId1": 19,
        "cId2": 19,
        "cardinality": 4,
        "distance": 0.0,
        "idx": 1
      },
      {
        "cId1": 20,
        "cId2": 20,
        "cardinality": 8,
        "distance": 0.0,
        "idx": 2
      },
      {
        "cId1": 21,
        "cId2": 21,
        "cardinality": 16,
        "distance": 0.0,
        "idx": 3
      },
      {
        "cId1": 22,
        "cId2": 22,
        "cardinality": 32,
        "distance": 0.0,
        "idx": 4
      },
      {
        "cId1": 5,
        "cId2": 9,
        "cardinality": 2,
        "distance": 0.02370862500000001,
        "idx": 5
      },
      {
        "cId1": 10,
        "cId2": 12,
        "cardinality": 2,
        "distance": 0.03236620000000001,
        "idx": 6
      },
      {
        "cId1": 6,
        "cId2": 25,
        "cardinality": 3,
        "distance": 0.08368415023774402,
        "idx": 7
      },
      {
        "cId1": 1,
        "cId2": 26,
        "cardinality": 4,
        "distance": 0.16811419652327603,
        "idx": 8
      },
      {
        "cId1": 2,
        "cId2": 24,
        "cardinality": 3,
        "distance": 0.19964830332279312,
        "idx": 9
      },
      {
        "cId1": 13,
        "cId2": 14,
        "cardinality": 2,
        "distance": 0.2161841,
        "idx": 10
      },
      {
        "cId1": 27,
        "cId2": 29,
        "cardinality": 6,
        "distance": 0.8909581133907116,
        "idx": 11
      },
      {
        "cId1": 17,
        "cId2": 28,
        "cardinality": 4,
        "distance": 1.220612393030742,
        "idx": 12
      },
      {
        "cId1": 18,
        "cId2": 31,
        "cardinality": 5,
        "distance": null,
        "idx": 13
      },
      {
        "cId1": 15,
        "cId2": 16,
        "cardinality": 2,
        "distance": null,
        "idx": 14
      },
      {
        "cId1": 11,
        "cId2": 30,
        "cardinality": 7,
        "distance": null,
        "idx": 15
      },
      {
        "cId1": 7,
        "cId2": 8,
        "cardinality": 2,
        "distance": null,
        "idx": 16
      },
      {
        "cId1": 3,
        "cId2": 4,
        "cardinality": 2,
        "distance": null,
        "idx": 17
      }
    ],
    "n": 19
  },
  "progress": 26,
  "state": "Approximating"
};
const fullData = {
  "hierarchy": {
    "hierarchy": [
      {
        "cId1": 5,
        "cId2": 9,
        "cardinality": 2,
        "distance": 0.02370862500000001,
        "idx": 0
      },
      {
        "cId1": 10,
        "cId2": 12,
        "cardinality": 2,
        "distance": 0.03236620000000001,
        "idx": 1
      },
      {
        "cId1": 3,
        "cId2": 17,
        "cardinality": 2,
        "distance": 0.05734499999999998,
        "idx": 2
      },
      {
        "cId1": 14,
        "cId2": 18,
        "cardinality": 2,
        "distance": 0.06235020000000002,
        "idx": 3
      },
      {
        "cId1": 6,
        "cId2": 20,
        "cardinality": 3,
        "distance": 0.08368415023774402,
        "idx": 4
      },
      {
        "cId1": 16,
        "cId2": 21,
        "cardinality": 3,
        "distance": 0.10444428027715698,
        "idx": 5
      },
      {
        "cId1": 1,
        "cId2": 23,
        "cardinality": 4,
        "distance": 0.16811419652327603,
        "idx": 6
      },
      {
        "cId1": 2,
        "cId2": 19,
        "cardinality": 3,
        "distance": 0.19964830332279312,
        "idx": 7
      },
      {
        "cId1": 13,
        "cId2": 22,
        "cardinality": 3,
        "distance": 0.2136299919050069,
        "idx": 8
      },
      {
        "cId1": 7,
        "cId2": 26,
        "cardinality": 4,
        "distance": 0.36576795948612656,
        "idx": 9
      },
      {
        "cId1": 24,
        "cId2": 25,
        "cardinality": 7,
        "distance": 0.6586118159377371,
        "idx": 10
      },
      {
        "cId1": 27,
        "cId2": 28,
        "cardinality": 7,
        "distance": 1.1484012185255947,
        "idx": 11
      },
      {
        "cId1": 29,
        "cId2": 30,
        "cardinality": 14,
        "distance": 2.133872650139606,
        "idx": 12
      },
      {
        "cId1": 15,
        "cId2": 31,
        "cardinality": 15,
        "distance": null,
        "idx": 13
      },
      {
        "cId1": 8,
        "cId2": 11,
        "cardinality": 2,
        "distance": null,
        "idx": 14
      },
      {
        "cId1": 0,
        "cId2": 4,
        "cardinality": 2,
        "distance": null,
        "idx": 15
      },
      {
        "cId1": 34,
        "cId2": 34,
        "cardinality": 4,
        "distance": null,
        "idx": 16
      },
      {
        "cId1": 35,
        "cId2": 35,
        "cardinality": 8,
        "distance": null,
        "idx": 17
      }
    ],
    "n": 19
  },
  "progress": 54,
  "state": "ComputingFull"
};

const smallHierarchy = {
  n: 5,
  hierarchy: [
    {
      "cId1": 0,
      "cId2": 3,
      "cardinality": 2,
      "distance": 0.024,
      "idx": 0
    },
    {
      "cId1": 1,
      "cId2": 2,
      "cardinality": 2,
      "distance": 0.033,
      "idx": 0
    },
    {
      "cId1": 4,
      "cId2": 5,
      "cardinality": 3,
      "distance": 0.1,
      "idx": 0
    },
    {
      "cId1": 6,
      "cId2": 7,
      "cardinality": 5,
      "distance": 0.23,
      "idx": 0
    }
  ]
};

const defaultState = {
  "hierarchy": {
    "hierarchy": [],
    "n": 0
  },
  "state": "Initializing",
  "progress": 0
};
defaultState.hierarchy = smallHierarchy;
const jobId = 0;

function DendoTest() {
  const [state, setState] = useState(defaultState);
  const [polling, setPolling] = useState(null);

  const reset = useCallback(() => {
    setState(defaultState);
    if (polling) {
      clearInterval(polling);
      setPolling(null);
    }
  }, [polling, setState, setPolling]);

  const toggle = useCallback(() => {
    console.debug("Polling...");
    setState(s => {
      if (s.state === "Initializing" || s.state === "ComputingFull") {
        return approxData;
      } else if (s.state === "Approximating") {
        return fullData;
      }
    })
  }, [setState]);

  const startPolling = useCallback((jobId) => {
    toast.info("Starting job " + jobId + " and polling ...");
    const inter = setInterval(toggle, 2000);
    setPolling(inter);
  }, [setState, setPolling]);

  const abortPolling = useCallback(reset, [polling, setState, setPolling]);

  return (
    <div className="m-5">
      <h1 className="text-3xl font-bold text-center">Dendrogram Test</h1>
      <div className="flex justify-center items-center m-10">
        <Button className="mx-auto" variant="primary" size="lg" disabled={!!polling} onClick={toggle}>
          Switch state
        </Button>
        <Button className="mx-auto" variant="primary" size="lg" disabled={!!polling} onClick={reset}>
          Reset
        </Button>
        <Button className="mx-auto" variant="secondary" disabled={!!polling} size="lg" onClick={startPolling}>
          Start Polling
        </Button>
        <Button className="mx-auto" variant="secondary" disabled={!polling} size="lg" onClick={abortPolling}>
          Abort Polling
        </Button>
      </div>
      <span className="text-lg font-bold">State: {state.state}</span>
      <Divider className="border-2" />
      {!state.hierarchy.hierarchy ? (<></>) : (
        <D3Dendrogram data={state.hierarchy} />
      )}
      <Divider className="border-2" />
    </div>
  )
}

export default DendoTest;
