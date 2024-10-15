import React, {useCallback, useState} from "react";
import {Button, Divider} from "@tremor/react";
import WidthProvider from "../components/WidthProvider";
import D3LineChart from "../components/D3LineChart";

const defaultState = [
  0,
  1,
  0.7084083122171766,
  0.590374834144184,
  0.7830266225003066,
  0.9243487506645401,
  0.716216824111561,
  0.8037911520883657,
  0.9101749236587107,
  0.5393231854145167,
  0.7828320802005012,
  0.5766417792733582,
  0.7356360014254752,
  0.4931647300068352,
  0.6649838929108115,
  0.4819542170935359,
  0.8016515648094595,
  0.5726431463273568,
  1,
  0.8680161943319837,
  0.9420739348370928,
  1,
  1,
  0.9398348813209495,
  0.9381578947368421,
  0.9418601651186792,
  0.6344683824946983,
  0.6877907065233935,
  0.23387467637101006,
  0.5881001846722068,
  1,
  1,
  1,
  0.5240031897926635,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  0.8947368421052632,
  1,
  0.5328947368421053,
  1,
  0.8947368421052632,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  0.9166666666666665,
  0.9671052631578947,
  1,
  1,
  0.8701754385964912,
  1,
  1,
  1,
  0.9,
  1,
  1,
  0.9665071770334928,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  0.8947368421052632,
  1,
  1,
  0.6333333333333333,
  1,
  0.75,
  1,
  1,
  1,
  1,
  0.6468671679197996,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  0.8947368421052632,
  0.6973684210526316,
  1,
  1,
  1,
  0.9707602339181286,
  1,
  1,
  1,
  1,
  0.8421052631578947,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  0.5448753462603877,
  1,
  1,
  1,
  1,
  1,
  0.8421052631578947,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  0.863157894736842,
  1,
  1,
  1,
  1,
  1,
  0.8246240601503759,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  0.7894736842105263,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  0.9064327485380117,
  1,
  0.6093567251461988,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  0.34649122807017546,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1,
  1
];

function LineChartTest() {
  const [state, setState] = useState(defaultState);
  const [running, setRunning] = useState(null);
  const [index, setIndex] = useState(20);

  const inc = useCallback(() => {
    console.debug("Running...");
    setIndex(i => {
      const newI = i + 1
      if (newI >= state.length) {
        onStop();
      }
      return newI;
    });
  }, [setIndex]);

  const onStart = useCallback(() => {
    console.log("Starting polling ...");
    setIndex(20);
    const inter = setInterval(inc, 500);
    setRunning(inter);
    inc();
  }, [setIndex, setRunning, inc]);

  const onStop = useCallback(() => {
    console.log("Stop polling")
    clearInterval(running);
    setRunning(null);
  }, [running, setRunning]);

  const similarities = state.slice(0, index);

  return (
    <div className="m-5">
      <h1 className="text-3xl font-bold text-center">LineChart Test</h1>
      <div className="flex justify-center items-center m-10">
        <Button className="mx-auto" variant="primary" size="lg" disabled={!!running} onClick={onStart}>
          Start running
        </Button>
        <Button className="mx-auto" variant="primary" size="lg" disabled={!running} onClick={onStop}>
          Stop running
        </Button>
      </div>
      <Divider className="border-2"/>
      <WidthProvider>
          {similarities.length === 0 ? (<></>) : (
            <D3LineChart data={similarities}/>
          )}
      </WidthProvider>
      <Divider className="border-2"/>
      <pre className="block m-0 pl-2 overscroll-y-auto">
        {JSON.stringify(similarities, null, 2)}
      </pre>
    </div>
)
}

export default LineChartTest;
