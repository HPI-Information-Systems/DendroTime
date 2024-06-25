import React, {useState} from "react";
import Header from "./Header";
import D3BarChart from "./components/D3BarChart";
import {Button} from '@tremor/react';

function App() {
  const [data, setData] = useState([12, 5, 6, 6, 9, 10]);

  const addData = () => {
    setData(d => d.concat([5]));
  }
  const removeData = () => {
    setData(d => d.length > 1 ? d.slice(0, -1) : d);
  }

  return (
    <div id={"App"} className="mx-auto max-w-screen-2xl">
      <Header/>
      <main className="lg:pl-72">
        <h1 className="text-3xl font-bold underline">Hello World</h1>
        <D3BarChart data={data}/>
        <div className="flex justify-center">
          <Button variant="primary" size="md" onClick={addData}>
            Add
          </Button>
          <Button variant="primary" size="md" onClick={removeData}>
            Remove
          </Button>
        </div>
      </main>
    </div>
  );
}

export default App;
