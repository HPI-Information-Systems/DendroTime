import React, {useContext, useEffect, useId} from "react";
import * as d3 from "d3";
import {WidthContext} from "./WidthProvider";

function getHierarchyRoot(data) {
  const {hierarchy: h, n: nLeafs} = data;
  const nClusters = h.length;
  const nodes = Array(nLeafs + nClusters);
  const reachable = Array(nLeafs + nClusters);

  // add leafs
  for (let i = 0; i < nLeafs; i++) {
    nodes[i] = {id: i, distance: 0.0, size: 1, children: []};
  }

  // add clusters
  for (let i = 0; i < nClusters; i++) {
    const node = h[i];
    const idx = nLeafs + i;
    if (node.distance?.valueOf() !== undefined) {
      reachable[node.cId1] = true;
      reachable[node.cId2] = true;
      nodes[idx] = {
        id: idx,
        distance: node.distance,
        size: node.cardinality,
        children: [
          nodes[node.cId1],
          nodes[node.cId2]
        ]
      };
    } else if (i === nClusters - 1) {
      const children = new Set();
      const c1 = nodes[node.cId1];
      if (c1) {
        reachable[node.cId1] = true;
        children.add(c1);
      }
      const c2 = nodes[node.cId2];
      if (c2) {
        reachable[node.cId2] = true;
        children.add(c2);
      }
      nodes[idx] = {
        id: idx,
        distance: node.distance,
        size: node.cardinality,
        children: children
      };
    }
  }

  // add non-reachable nodes to root
  const root = nodes[nodes.length - 1];
  reachable[nodes.length - 1] = true; // is the root and thus always reachable
  for (let i = 0; i < nodes.length; i++) {
    if (!reachable[i]) {
      const c1 = nodes[i];
      if (c1) {
        root.children.add(c1);
        root.size += 1;
      }
    }
  }

  // repair distance of root: move to top
  if (root.distance <= 0) {
    const maxDistance = nodes
      .map(n => isNaN(n.distance) ? 0 : n.distance)
      .reduceRight((a, b) => a>b? a:b, 0)
    root.distance = 1.1*maxDistance;
  }
  return root;
}

function D3Dendrogram({data, useEqualNodeDistance, showLabels = false}) {
  const id = useId().replaceAll(":", "");
  // fixed width of outer div, the height is dynamic on dataset size!
  const width = useContext(WidthContext);
  const horizMargin = 50;
  const axisHeight = 50;
  const tDuration = 200;
  const dx = 5;
  const vertMargin = dx;
  const axisFontSize = "15px";

  const hierarchy = getHierarchyRoot(data);
  // optimize: useMemo
  const root = d3.hierarchy(hierarchy);
  // console.log("Root:", root);

  /////////////////////////////////////////////////////////////////////////////
  // stateful drawing!!
  useEffect(() => {
    const dy = (width - 2*horizMargin) / (root.height);

    root.sort((a, b) => b.height - a.height || d3.ascending(a.id, b.id));
    // overwrite value property with our own computation:
    root.sum(() => 0).each(d => {d.value = d.data.distance;})
    // root.sort((a, b) => b.data.idx);
    const tree = d3.cluster()
      .nodeSize([dx, dy])
      .separation((a, b) => 1);
    tree(root);

    // Compute the extent of the tree. Note that x and y are swapped here
    // because in the tree layout, x is the breadth, but when displayed, the
    // tree extends left rather than down.
    let top = Infinity;
    let bottom = -top;
    root.each(d => {
      if (d.x > bottom) bottom = d.x;
      if (d.x < top) top = d.x;
    });
    // add space for the axis to the top
    top -= axisHeight/2 + dx/2;
    const height = bottom - top + vertMargin;
    const viewPort = [-horizMargin, top, width, height]
      .toString()
      .replaceAll("[", "")
      .replaceAll("]", "");
    // console.log("viewPort", viewPort);

    const svg = d3.select("#" + id)
      .attr("height", height)
      .attr("viewBox", viewPort);

    // const yScale = d3.scaleLinear()
    //   .domain([1, 0])
    //   .range([0, height]);
    // const yAxis = svg.call(d3.axisLeft(yScale))

    // console.log("New axis location", top + axisHeight/2);
    const maxDistance = useEqualNodeDistance ? root.height : root.value;
    const fx = d => useEqualNodeDistance ? d.y : xScale(d.data.distance);
    const fy = d => d.x;
    const xScale = d3.scaleLinear()
      .domain([maxDistance, 0])
      .range([0, width-2*horizMargin]);
    const xAxis = d3.select(`#${id}-axis`)
      // .transition().duration(tDuration)
      .attr("transform", `translate(0,${top + axisHeight/2})`)
      .call(d3.axisTop(xScale))
      .style("font-size", axisFontSize);
    const connection = d3.linkHorizontal().x(fx).y(fy);


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

    const addTextNode = (selection) => {
      selection.append("text")
        .attr("dy", "0.31em")
        .attr("x", d => d.children ? -6 : 6)
        .attr("text-anchor", d => d.children ? "end" : "start")
        .text(d => d.data.id)
        .attr("paint-order", "stroke");
    };

    const nodes = d3.select(`#${id}-nodes`).selectAll("g")
      .data(root.descendants(), d => d? d.id: this.id)
      .join(
        enter => {
          const selection = enter.append("g");
          selection//.transition().duration(tDuration)
            .attr("transform", d => `translate(${fx(d)},${fy(d)})`)
            .attr("id", d => d.data.id);
          const leaves = selection.filter(d => !d.children);
          if (showLabels) {
            addTextNode(leaves);
          } else {
            leaves.on("mouseover", function () { addTextNode(d3.select(this)) });
            leaves.on("mouseout", function () { d3.select(this).select("text").remove() });
        }
          return selection;
        },
        update => update.transition().duration(tDuration)
          .attr("transform", d => `translate(${fx(d)},${fy(d)})`)
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
  }, [id, data, width, useEqualNodeDistance]);
  /////////////////////////////////////////////////////////////////////////////

  return (
    <div className="text-sm mb-2">
      <svg id={id} width={width}>
        <g id={`${id}-axis`}/>
        <g id={`${id}-links`}/>
        <g id={`${id}-nodes`}/>
      </svg>
    </div>
  );
}

export default D3Dendrogram;
