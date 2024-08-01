import React, {useEffect, useId, useContext} from "react";
import * as d3 from "d3";
import { WidthContext } from "./WidthProvider";

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

  // repair distance of root: move to top
  if (root.distance <= 0) {
    const maxDistance = tree
      .map(n => isNaN(n.distance) ? 0 : n.distance)
      .reduceRight((a, b) => a>b? a:b, 0)
    root.distance = 1.1*maxDistance;
    console.log(maxDistance, root.distance);
  }
  return root;
}

function D3Dendrogram({data}) {
  const id = useId().replaceAll(":", "");
  // fixed width of outer div, the height is dynamic on dataset size!
  const width = useContext(WidthContext);
  const horizMargin = 30;
  const axisHeight = 50;
  const tDuration = 500;

  /////////////////////////////////////////////////////////////////////////////
  // stateful drawing!!
  useEffect(() => {
    // optimize: useMemo
    const hierarchy = getHierarchyRoot(data);
    const root = d3.hierarchy(hierarchy);
    console.log("Root:", root);

    const dx = 15;
    const vertMargin = 2*dx;
    const dy = (width - 2*horizMargin) / (root.height);

    root.sort((a, b) => b.height - a.height || d3.ascending(a.id, b.id));
    // overwrite value property with our own computation:
    root.sum(() => 0).each(d => {d.value = d.data.distance;})
    // root.sort((a, b) => b.data.idx);
    const tree = d3.cluster().nodeSize([dx, dy])
    .separation((a, b) => 1);
    tree(root);
    const maxDistance = root.value;
    console.log("Max distance", maxDistance);

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
    const viewPort = [-horizMargin, top, width, height]
      .toString()
      .replaceAll("[", "")
      .replaceAll("]", "");
    console.log("viewPort", viewPort);

    const svg = d3.select("#" + id)
      .attr("height", height)
      .attr("viewBox", viewPort);

    // const yScale = d3.scaleLinear()
    //   .domain([1, 0])
    //   .range([0, height]);
    // const yAxis = svg.call(d3.axisLeft(yScale))

    console.log("New axis location", top + axisHeight/2);
    const xScale = d3.scaleLinear()
      .domain([maxDistance, 0])
      .range([0, width-2*horizMargin]);
    const xAxis = d3.select(`#${id}-axis`)
      .transition().duration(tDuration)
      .attr("transform", `translate(0,${top + axisHeight/2})`)
      .call(d3.axisTop(xScale));
    const t = d3.transition().duration(tDuration);
    const connection = d3.linkHorizontal()
      .x(d => xScale(d.data.distance))
      .y(d => d.x);

    const links = d3.select(`#${id}-links`).selectAll("path")
      .data(root.links(), d => d? d.id : this.id)
      .join(
        enter => enter
          .append("path")
          .transition().duration(tDuration)
            .attr("d", connection)
            .attr("id", d => d.id),
        update => update.transition().duration(tDuration)
          .attr("d", connection)
          .attr("id", d => d.id),
        // exit => exit.transition().duration(tDuration).remove()
      )
        .order()
        .attr("fill", "none")
        .attr("stroke", "#555")
        .attr("stroke-opacity", 0.4)
        .attr("stroke-width", 1.5);

    const nodes = d3.select(`#${id}-nodes`).selectAll("g")
      .data(root.descendants(), d => d? d.id: this.id)
      .join(
        enter => {
          const selection = enter.append("g");
          selection//.transition().duration(tDuration)
            .attr("transform", d => `translate(${xScale(d.data.distance)},${d.x})`)
            .attr("id", d => d.data.id);
          selection.append("text")
              .attr("fill", "black")
              .attr("dy", "0.31em")
              .attr("x", d => d.children ? -6 : 6)
              .attr("text-anchor", d => d.children ? "end" : "start")
              .text(d => d.data.id)
              .attr("stroke", "white")
              .attr("paint-order", "stroke");
          return selection;
        },
        update => update.transition().duration(tDuration)
          .attr("transform", d => `translate(${xScale(d.data.distance)},${d.x})`)
          .attr("id", d => d.data.id),
        // exit => exit.transition().duration(tDuration).remove()
      )
      .order()
      .attr("stroke-linejoin", "round")
      .attr("stroke-width", 3)
      .append("circle")
        .attr("r", 2.5)
        .attr("fill", d => d.children ? "#555" : "#999");

    // nodes.exit().transition().duration(tDuration).remove()
    //   .selectAll("circle").remove();
  }, [id, data, width]);
  /////////////////////////////////////////////////////////////////////////////

  return (
    <div className="text-sm border-4 border-blue-500">
      <svg id={id} width={width}>
        <g id={`${id}-axis`}/>
        <g id={`${id}-links`}/>
        <g id={`${id}-nodes`}/>
      </svg>
    </div>
  );
}

export default D3Dendrogram;
