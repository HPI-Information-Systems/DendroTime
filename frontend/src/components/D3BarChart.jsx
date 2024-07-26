import React from "react";
import { useEffect } from 'react';
import * as d3 from "d3";

function D3BarChart({data}) {
  const width = 700;
  const height = 300;
  const margin = {top: 20, right: 20, bottom: 30, left: 40};
  const innerWidth = 700 - margin.left - margin.right;
  const innerHeight = 300 - margin.top - margin.bottom;
  const [selected, setSelected] = React.useState(null);

  if (selected && selected.value !== data[data.indexOf(selected.value)]) {
    setSelected(null);
  }

  function handleBarClick(e, d) {
    setSelected({value: d, domElement: this});
  }

  function drawChart(id) {
    const svg = d3.select("#" + id);
    const barWidth = innerWidth / (data.length+1);
    const xScale = d3.scaleLinear()
      .domain([0, data.length])
      .range([0, innerWidth]);
    const yScale = d3.scaleLinear()
      .domain([Math.max(...data), 0])
      .range([0, innerHeight]);

    const yAxis = svg.call(d3.axisLeft(yScale))
    // svg.append("g").attr("transform", "translate(0," + height + ")").call(d3.axisBottom(xScale));
    const t = d3.transition().duration(1000);
    function bars(x) {
      x.attr("x", (d, i) => xScale(i))
        .attr("y", (d, i) => yScale(d))
        .attr("height", (d, i) => innerHeight - yScale(d))
        .attr("width", barWidth);
    }

    const target = svg.selectAll("rect").data(data);

    target.exit().transition().remove();

    const newData = target.enter()
      .append("rect")
      .text(d => d)
      .attr("fill", "green")
      .on("click", handleBarClick);
    bars(newData);

    bars(
      newData.merge(target).transition(t)
    );

    target.filter((d, i) => selected && d === selected.value)
      .attr("stroke", "black");
    target.filter((d, i) => selected && d !== selected.value)
      .attr("stroke", "green");
  }

  useEffect(() => {
    drawChart("d3-canvas");
  }, [data, selected]);

  return (
    <div>
      <svg width={width} height={height}>
        <g id="d3-canvas" transform={"translate(" + margin.left + "," + margin.top + ")"}></g>
      </svg>
      <div >
        <p>{selected ? "Selected bar at index " + data.indexOf(selected.value) + ": " + selected.value : "Nothing selected"}</p>
      </div>
    </div>
  );
}

export default D3BarChart;
