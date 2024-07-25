import React, {useEffect, useState} from "react";
import StatusCard from "../primitives/StatusCard";

const defaultStatusList = [{
  "name": "Initializing",
  "active": true,
  "progress": 100,
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

export default function StatusOverview({jobId, progressPlaceholder}) {
  const [statusList, setStatusList] = useState(defaultStatusList);

  // function pollStatus() {
  //   fetch("/api/jobs/" + jobId, {method: "GET", headers: {'Content-Type': 'application/json'}, mode: 'no-cors'})
  //     .then((res) => res.json())
  //     .then((data) => setStatusList(current => current.map(status => {
  //       const newStatus = {...status};
  //       if (data.status == status.name){
  //         newStatus.active = true;
  //         newStatus.progress = data.progress;
  //       } else {
  //         newStatus.active = false;
  //       }
  //       return newStatus;
  //     })));
  // };
  function pollStatus() {
    const data = {
      "status": "Approximating",
      "progress": progressPlaceholder
    };
    setStatusList(current => current.map(status => {
      const newStatus = {...status};
      if (data.status === status.name){
        newStatus.active = true;
        newStatus.progress = data.progress;
      } else {
        newStatus.active = false;
      }
      return newStatus;
    }))
  }

  // load initial status
  useEffect(pollStatus, [progressPlaceholder]);

  return (
    <div className="flex m-1">
      {statusList.map((item, i, _array) => (
        <React.Fragment key={item.name}>
          <StatusCard name={item.name} active={item.active} progress={item.progress}/>
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