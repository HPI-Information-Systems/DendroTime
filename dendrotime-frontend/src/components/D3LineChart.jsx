import React, {useContext, useEffect, useId} from "react";
import * as d3 from "d3";
import {WidthContext} from "./WidthProvider";

function D3LineChart({data, useTimestamps}) {
  const id = useId().replaceAll(":", "");
  // use width of outer div
  const width = useContext(WidthContext);
  const height = 120;
  const horizMargin = 50;
  const axisHeight = 50;
  const legendWidth = 150;
  const tDuration = 200;
  const axisFontSize = "15px";

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

    const index = useTimestamps? data.timestamps : data.steps;
    const minIndex = useTimestamps? data.timestamps[0] : 0;
    const array = [
      data.hierarchySimilarities.map((x, i) => [index[i] - minIndex, x]),
      data.hierarchyQualities.map((x, i) => [index[i] - minIndex, x]),
      data.clusterQualities.map((x, i) => [index[i] - minIndex, x]),
    ];
    const allLabels = ["Hierarchy Similarity", "Hierarchy Quality", "Cluster Quality"];
    const labels = [];
    if (data.hierarchySimilarities.length > 0) labels.push(allLabels[0]);
    if (data.hierarchyQualities.length > 0) labels.push(allLabels[1]);
    if (data.clusterQualities.length > 0) labels.push(allLabels[2]);

    const maxIndex = array
      .map(x => d3.max(x, d => d[0]) || 0)
      .reduce((a, b) => Math.max(a, b), 0);
    const xScale = d3.scaleLinear()
      .domain([0, maxIndex])
      .range([0, width-2*horizMargin-legendWidth]);
    const xAxis = d3.select(`#${id}-xaxis`);
    xAxis.attr("transform", `translate(0,${height - axisHeight/2})`)
      .transition().duration(tDuration)
      .call(d3.axisBottom(xScale))
      .style("font-size", axisFontSize);
    // add axis label
    const xLabel = xAxis.select(".label").node() ?
      xAxis.select(".label").select("text")
      : xAxis.append("g").attr("class", "label").append("text");
    xLabel
      .attr("text-anchor", "start")
      .attr("x", width-legendWidth-2*horizMargin + 25)
      .attr("dy", "10")
      .attr("fill", "currentColor")
      .text(useTimestamps? "Timestamps in ms" : "Steps");


    const yScale = d3.scaleLinear()
      .domain([0, 1])
      .range([height - axisHeight, 0]);
    const yAxis = d3.select(`#${id}-yaxis`)
      .attr("transform", `translate(0,${top + axisHeight/2})`)
      .call(d3.axisLeft(yScale).ticks(5))
      .style("font-size", axisFontSize);

    const colorScale = d3.scaleOrdinal()
      .domain(allLabels)
      .range(d3.schemeSet2);
    const legend = d3.select(`#${id}-legend`).selectAll("g")
      .data(labels)
      .join(
        enter => {
          const g = enter.append("g").attr("id", d => d);
          g.append("circle")
            .attr("r", 5);
          g.append("text")
            .attr("dy", "0.31em")
            .attr("x", 10)
            .attr("text-anchor", "start")
            .attr("paint-order", "stroke");
          return g;
        }
      )
      .attr("transform", (d, i) => `translate(${width-legendWidth-2*horizMargin+25},${top + axisHeight/2 + i*20})`);
    legend.select("circle").attr("fill", d => colorScale(d));
    legend.select("text").text(d => d);

    const connection = d3.line()
      .x((d, i) => xScale(d[0]))
      .y(d => yScale(d[1]) + axisHeight/ 2);

    const lines = d3.select(`#${id}-line`).selectAll("path")
      .data(array)
      .join(
        enter => enter.append("path")
          .transition().duration(tDuration)
          .attr("d", connection),
        update => update
          .transition().duration(tDuration)
          .attr("d", connection)
      )
      .attr("fill", "none")
      .attr("stroke", (d, i) => colorScale(allLabels[i]))
      .attr("stroke-opacity", 1)
      .attr("stroke-width", 1.5);
  }, [id, data, width]);
  /////////////////////////////////////////////////////////////////////////////

  return (
    <div className="text-sm mb-2">
      <svg id={id} width={width} height={height}>
        <g id={`${id}-xaxis`}/>
        <g id={`${id}-yaxis`}/>
        <g id={`${id}-line`}/>
        <g id={`${id}-legend`}/>
      </svg>
    </div>
  );
}

export default D3LineChart;
