import React, {useEffect, useState, useReducer} from "react";
import StatusCard from "../primitives/StatusCard";

const defaultStatusList = [{
  "name": "Initializing",
  "active": true,
  "progress": 0,
},{
  "name": "Approximating",
  "active": false,
  "progress": 0
},{
  "name": "Computing",
  "active": false,
  "progress": 0
},{
  "name": "Finalizing",
  "active": false,
  "progress": 0
}];

function statusReducer(state, action) {
  const {type, data} = action;
  switch (type) {
    case "nextState":
      // set old state to 100 % progress and switch state
      return state.map(s => {
        const updatedS = {...s};
        if (s.active) updatedS.progress = 100;
        updatedS.active = s.name === data;
        return updatedS;
      })
    default:
      return state;
  }
}

export default function StatusOverview({jobId, progress, activeState}) {
  const [statusList, dispatch] = useReducer(statusReducer, defaultStatusList);
  const [active, setActive] = useState("Initializing");

  if (active !== activeState) {
    dispatch({type: "nextState", data: activeState});
    setActive(activeState);
  }

  return (
    <div className="flex m-1">
      {statusList.map((item, i, _array) => (
        <React.Fragment key={item.name}>
          <StatusCard name={item.name} active={item.active} progress={item.active? progress : item.progress}/>
          {i !== statusList.length - 1 ? (
            <svg className="rtl:rotate-180 w-32 h-32 text-gray-400 mx-1 m-1" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 6 10">
              <path stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="m1 9 4-4-4-4"/>
            </svg>
          ) : null}
        </React.Fragment>
      ))}
    </div>
  )
}