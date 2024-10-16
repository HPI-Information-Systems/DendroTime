import React, {useContext, useEffect, useId} from "react";
import * as d3 from "d3";
import {WidthContext} from "./WidthProvider";

function D3LineChart({data}) {
  const id = useId().replaceAll(":", "");
  // use width of outer div
  const width = useContext(WidthContext);
  const height = 200;
  const horizMargin = 50;
  const axisHeight = 50;
  const tDuration = 200;

  /////////////////////////////////////////////////////////////////////////////
  // stateful drawing!!
  useEffect(() => {
    let top = 0;
    const viewPort = [-horizMargin, top, width, height]
      .toString()
      .replaceAll("[", "")
      .replaceAll("]", "");
    // console.log("viewPort", viewPort);

    const svg = d3.select("#" + id)
      .attr("height", height)
      .attr("viewBox", viewPort);

    const maxIndex = d3.max(data, d => d[0]);
    const xScale = d3.scaleLinear()
      .domain([0, maxIndex])
      .range([0, width-2*horizMargin]);
    const xAxis = d3.select(`#${id}-xaxis`)
      .attr("transform", `translate(0,${height - axisHeight/2})`)
      .transition().duration(tDuration)
      .call(d3.axisBottom(xScale));

    const yScale = d3.scaleLinear()
      .domain([0, 1])
      .range([height - axisHeight, 0]);
    const yAxis = d3.select(`#${id}-yaxis`)
      .attr("transform", `translate(0,${top + axisHeight/2})`)
      .call(d3.axisLeft(yScale))

    const connection = d3.line()
      .x((d, i) => xScale(d[0]))
      .y(d => yScale(d[1]) + axisHeight/ 2);

    const lines = d3.select(`#${id}-line`).selectAll("path")
      .data([data.sort((a, b) => a[0] - b[0])])
      .join(
        enter => enter.append("path")
          .transition().duration(tDuration)
          .attr("d", connection),
        update => update
          .transition().duration(tDuration)
          .attr("d", connection)
      )
      .attr("fill", "none")
      .attr("stroke", "#2c3986")
      .attr("stroke-opacity", 1)
      .attr("stroke-width", 1.5);

  }, [id, data, width]);
  /////////////////////////////////////////////////////////////////////////////

  return (
    <div className="text-sm border-4 border-blue-500 bg-gray-100">
      <svg id={id} width={width} height={height}>
        <g id={`${id}-xaxis`}/>
        <g id={`${id}-yaxis`}/>
        <g id={`${id}-line`}/>
      </svg>
    </div>
  );
}

export default D3LineChart;
