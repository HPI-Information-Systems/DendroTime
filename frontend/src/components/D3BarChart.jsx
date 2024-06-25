import React from "react";
import { useEffect } from 'react';
import * as d3 from "d3";

function D3BarChart({data}) {
  const width = 700;
  const height = 300;

  const drawChart = (id) => {
    console.log("Drawing chart", data);
    const svg = d3.select("#" + id);
    svg.selectAll("rect")
        .data(data)
        .enter()
        .append("rect")
        .attr("x", (d, i) => i * 70)
        .attr("y", (d, i) => 300 - 10 * d)
        .text((d) => d)
        .attr("width", 65)
        .attr("height", (d, i) => d * 10)
        .attr("fill", "green");
  }
  useEffect(() => {
    drawChart("d3-canvas");
  }, [data]);

  return (
    <div><svg id="d3-canvas" width={width} height={height}></svg></div>
  )
}

export default D3BarChart;
