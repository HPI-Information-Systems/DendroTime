import React, {useState} from "react";
import Header from "./components/Header";
import D3BarChart from "./components/D3BarChart";
import {Button} from '@tremor/react';

function App() {
  const [data, setData] = useState([12, 5, 6, 6, 9, 10]);

  const addData = () => {
    setData(d => d.concat([Math.trunc(Math.random()*50)]));
  }
  const removeData = () => {
    setData(d => d.length > 1 ? d.slice(0, -1) : d);
  }
  const changeData = () => {
    setData(d => {
      const newD = [...d];
      newD[Math.trunc(Math.random()*newD.length)] += 10;
      return newD;
    })
  }

  return (
    <div id={"App"} className="mx-auto max-w-screen-2xl">
      <Header/>
      <main className="lg:pl-72">
        <h1 className="text-3xl font-bold underline">Hello World</h1>
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
      </main>
    </div>
  );
}

export default App;
