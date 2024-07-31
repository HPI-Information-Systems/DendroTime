import React, {useCallback, useEffect, useId, useRef} from "react";
import * as d3 from "d3";
import {useDimensions} from "../util";

function getHierarchyRoot(data) {
  const {hierarchy: h, n: nLeafs} = data;
  const nClusters = h.length;
  console.log("nLeafs:", nLeafs, "nClusters:", nClusters);
  const tree = Array(nLeafs + nClusters);

  // add leafs
  for (let i = 0; i < nLeafs; i++) {
    tree[i] = {id: i, distance: 0.0, size: 1, children: []};
  }
  const root = {
    id: tree.length - 1,
    distance: 0.0,
    size: nLeafs,
    children: []
  }
  tree[tree.length - 1] = root;

  // add clusters
  for (let i = 0; i < nClusters; i++) {
    const node = h[i];
    const idx = nLeafs + i;
    if (node.distance?.valueOf() === undefined) {
      const c1 = tree[node.cId1];
      if (c1) root.children.push(c1);
      const c2 = tree[node.cId2];
      if (c2) root.children.push(c2);
    } else if (i === nClusters - 1) {
      root.distance = node.distance;
      root.size = node.cardinality;
      const c1 = tree[node.cId1];
      if (c1) root.children.push(c1);
      const c2 = tree[node.cId2];
      if (c2) root.children.push(c2);
    } else {
      tree[idx] = {
        id: idx,
        distance: node.distance,
        size: node.cardinality,
        children: [
          tree[node.cId1],
          tree[node.cId2]
        ]
      };
    }
  }
  return root;
}

function D3Dendrogram({data}) {
  const id = useId().replaceAll(":", "");

  // optimize: useMemo
  const hierarchy = getHierarchyRoot(data);
  const maxDistance = Math.max(...data.hierarchy.map(obj => obj.distance), 0);
  console.log(hierarchy);

  const root = d3.hierarchy(hierarchy);
  console.log("Root:", root);

  // the width of the diagram is fixed, the height is dynamic!
  const width = 900;
  const horizMargin = 20;
  const axisHeight = 50;
  const dx = 15;
  const vertMargin = 2*dx;
  const dy = (width - 2*horizMargin) / (root.height);

  root.sort((a, b) => b.height - a.height || d3.ascending(a.id, b.id));
  const tree = d3.cluster().nodeSize([dx, dy])(root);

  // Compute the extent of the tree. Note that x and y are swapped here
  // because in the tree layout, x is the breadth, but when displayed, the
  // tree extends right rather than down.
  let top = Infinity;
  let bottom = -top;
  root.each(d => {
    if (d.x > bottom) bottom = d.x;
    if (d.x < top) top = d.x;
  });
  // add space for the axis to the top
  top -= axisHeight;
  const height = bottom - top + vertMargin;
  console.log("height", height, "x1", bottom, "x0", top, "dx", dx);
  const viewPort = [-horizMargin, top, width, height]
    .toString()
    .replaceAll("[", "")
    .replaceAll("]", "");

  /////////////////////////////////////////////////////////////////////////////
  // stateful drawing!!
  const drawChart = useCallback((id) =>{
    const svg = d3.select("#" + id);

    const xScale = d3.scaleLinear()
      .domain([maxDistance, 0])
      .range([0, width-2*horizMargin]);
    const yScale = d3.scaleLinear()
      .domain([1, 0])
      .range([0, height]);

    // const yAxis = svg.call(d3.axisLeft(yScale))
    const xAxis = d3.select(`#${id}-axis`)
      .transition().duration(100)
      .attr("transform", `translate(0,${-dx*data.n/2-axisHeight/2})`)
      .call(d3.axisTop(xScale));
    const t = d3.transition().duration(100);

    const link = d3.select(`#${id}-links`).selectAll()
      .data(root.links())
      .join("path").transition().duration(100)
        .attr("fill", "none")
        .attr("stroke", "#555")
        .attr("stroke-opacity", 0.4)
        .attr("stroke-width", 1.5)
        .attr("d", d3.linkHorizontal()
          .x(d => d.y)
          .y(d => d.x)
      );

    const node = d3.select(`#${id}-nodes`).selectAll()
      .data(root.descendants())
      .join("g");

    node
      .attr("stroke-linejoin", "round")
      .attr("stroke-width", 3)
      .attr("transform", d => `translate(${d.y},${d.x})`);

    node.append("circle")
      .attr("fill", d => d.children ? "#555" : "#999")
      .attr("r", 2.5);

      // .transition(t)
    node.append("text")
      .attr("fill", "black")
      .attr("dy", "0.31em")
      .attr("x", d => d.children ? -6 : 6)
      .attr("text-anchor", d => d.children ? "end" : "start")
      .text(d => d.data.id)
      .attr("stroke", "white")
      .attr("paint-order", "stroke");
  }, [])

  useEffect(() => {
    drawChart(id);
  }, [id, data, maxDistance, root, width, horizMargin, height, dx, axisHeight]);
  /////////////////////////////////////////////////////////////////////////////

  return (
    <div className="text-sm border-4 border-blue-500">
      <svg width="100%" height={height} viewBox={viewPort}>
        <g id={`${id}-axis`}/>
        <g id={`${id}-links`}/>
        <g id={`${id}-nodes`}/>
      </svg>
    </div>
  );
}

export default D3Dendrogram;
