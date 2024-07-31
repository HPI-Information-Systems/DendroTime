import React, {useEffect, useId} from "react";
import * as d3 from "d3";

const demoData = {
  "n": 100,
  "hierarchy": [
    {
      "cId1": 50,
      "cId2": 52,
      "cardinality": 2,
      "distance": 0.00011579999999999924,
      "idx": 0
    },
    {
      "cId1": 5,
      "cId2": 88,
      "cardinality": 2,
      "distance": 0.00023410999999995408,
      "idx": 1
    },
    {
      "cId1": 46,
      "cId2": 89,
      "cardinality": 2,
      "distance": 0.00023626000000009917,
      "idx": 2
    },
    {
      "cId1": 14,
      "cId2": 42,
      "cardinality": 2,
      "distance": 0.0006028399999999934,
      "idx": 3
    },
    {
      "cId1": 18,
      "cId2": 48,
      "cardinality": 2,
      "distance": 0.0007279900000000117,
      "idx": 4
    },
    {
      "cId1": 9,
      "cId2": 45,
      "cardinality": 2,
      "distance": 0.0009933800000000437,
      "idx": 5
    },
    {
      "cId1": 63,
      "cId2": 66,
      "cardinality": 2,
      "distance": 0.0010540900000000075,
      "idx": 6
    },
    {
      "cId1": 7,
      "cId2": 53,
      "cardinality": 2,
      "distance": 0.0010981800000000597,
      "idx": 7
    },
    {
      "cId1": 6,
      "cId2": 99,
      "cardinality": 3,
      "distance": 0.0016098026615705943,
      "idx": 8
    },
    {
      "cId1": 70,
      "cId2": 84,
      "cardinality": 2,
      "distance": 0.0018868400000000562,
      "idx": 9
    },
    {
      "cId1": 41,
      "cId2": 102,
      "cardinality": 3,
      "distance": 0.0021978223637349977,
      "idx": 10
    },
    {
      "cId1": 3,
      "cId2": 85,
      "cardinality": 2,
      "distance": 0.0023939199999999383,
      "idx": 11
    },
    {
      "cId1": 73,
      "cId2": 74,
      "cardinality": 2,
      "distance": 0.002534690000000006,
      "idx": 12
    },
    {
      "cId1": 10,
      "cId2": 103,
      "cardinality": 3,
      "distance": 0.0032390793477211203,
      "idx": 13
    },
    {
      "cId1": 2,
      "cId2": 81,
      "cardinality": 2,
      "distance": 0.003399180000000057,
      "idx": 14
    },
    {
      "cId1": 43,
      "cId2": 78,
      "cardinality": 2,
      "distance": 0.003568349999999998,
      "idx": 15
    },
    {
      "cId1": 13,
      "cId2": 108,
      "cardinality": 3,
      "distance": 0.003860417933920258,
      "idx": 16
    },
    {
      "cId1": 59,
      "cId2": 106,
      "cardinality": 3,
      "distance": 0.003957458967165723,
      "idx": 17
    },
    {
      "cId1": 35,
      "cId2": 64,
      "cardinality": 2,
      "distance": 0.0040715550000000045,
      "idx": 18
    },
    {
      "cId1": 39,
      "cId2": 56,
      "cardinality": 2,
      "distance": 0.004156619999999944,
      "idx": 19
    },
    {
      "cId1": 93,
      "cId2": 97,
      "cardinality": 2,
      "distance": 0.00446342000000001,
      "idx": 20
    },
    {
      "cId1": 1,
      "cId2": 107,
      "cardinality": 4,
      "distance": 0.00469796210982481,
      "idx": 21
    },
    {
      "cId1": 91,
      "cId2": 105,
      "cardinality": 3,
      "distance": 0.005385944776697341,
      "idx": 22
    },
    {
      "cId1": 16,
      "cId2": 17,
      "cardinality": 2,
      "distance": 0.005505609999999994,
      "idx": 23
    },
    {
      "cId1": 65,
      "cId2": 111,
      "cardinality": 3,
      "distance": 0.005642900287502843,
      "idx": 24
    },
    {
      "cId1": 31,
      "cId2": 37,
      "cardinality": 2,
      "distance": 0.006717259999999947,
      "idx": 25
    },
    {
      "cId1": 44,
      "cId2": 86,
      "cardinality": 2,
      "distance": 0.0067647900000000205,
      "idx": 26
    },
    {
      "cId1": 100,
      "cId2": 110,
      "cardinality": 4,
      "distance": 0.0073659949792305845,
      "idx": 27
    },
    {
      "cId1": 71,
      "cId2": 109,
      "cardinality": 4,
      "distance": 0.008604975816739135,
      "idx": 28
    },
    {
      "cId1": 101,
      "cId2": 120,
      "cardinality": 6,
      "distance": 0.009679432244962344,
      "idx": 29
    },
    {
      "cId1": 57,
      "cId2": 118,
      "cardinality": 3,
      "distance": 0.011888808290158119,
      "idx": 30
    },
    {
      "cId1": 67,
      "cId2": 80,
      "cardinality": 2,
      "distance": 0.01303683,
      "idx": 31
    },
    {
      "cId1": 114,
      "cId2": 123,
      "cardinality": 5,
      "distance": 0.013049932179412261,
      "idx": 32
    },
    {
      "cId1": 33,
      "cId2": 124,
      "cardinality": 3,
      "distance": 0.01379635048554987,
      "idx": 33
    },
    {
      "cId1": 22,
      "cId2": 92,
      "cardinality": 2,
      "distance": 0.014080651999999999,
      "idx": 34
    },
    {
      "cId1": 34,
      "cId2": 98,
      "cardinality": 2,
      "distance": 0.015168300000000023,
      "idx": 35
    },
    {
      "cId1": 29,
      "cId2": 69,
      "cardinality": 2,
      "distance": 0.015584750000000008,
      "idx": 36
    },
    {
      "cId1": 115,
      "cId2": 126,
      "cardinality": 7,
      "distance": 0.016298777395298378,
      "idx": 37
    },
    {
      "cId1": 104,
      "cId2": 113,
      "cardinality": 4,
      "distance": 0.016496263803928473,
      "idx": 38
    },
    {
      "cId1": 112,
      "cId2": 122,
      "cardinality": 5,
      "distance": 0.01776148871576271,
      "idx": 39
    },
    {
      "cId1": 72,
      "cId2": 131,
      "cardinality": 6,
      "distance": 0.023309578671027228,
      "idx": 40
    },
    {
      "cId1": 96,
      "cId2": 129,
      "cardinality": 4,
      "distance": 0.024196480174951386,
      "idx": 41
    },
    {
      "cId1": 117,
      "cId2": 119,
      "cardinality": 4,
      "distance": 0.02573457076661705,
      "idx": 42
    },
    {
      "cId1": 95,
      "cId2": 132,
      "cardinality": 4,
      "distance": 0.027654069668825672,
      "idx": 43
    },
    {
      "cId1": 49,
      "cId2": 142,
      "cardinality": 5,
      "distance": 0.036076983826247924,
      "idx": 44
    },
    {
      "cId1": 60,
      "cId2": 61,
      "cardinality": 2,
      "distance": 0.036077179999999986,
      "idx": 45
    },
    {
      "cId1": 127,
      "cId2": 138,
      "cardinality": 9,
      "distance": 0.03747882994574106,
      "idx": 46
    },
    {
      "cId1": 116,
      "cId2": 140,
      "cardinality": 7,
      "distance": 0.043337013941785775,
      "idx": 47
    },
    {
      "cId1": 20,
      "cId2": 141,
      "cardinality": 5,
      "distance": 0.047613480276106714,
      "idx": 48
    },
    {
      "cId1": 21,
      "cId2": 25,
      "cardinality": 2,
      "distance": 0.051665659999999974,
      "idx": 49
    },
    {
      "cId1": 136,
      "cId2": 137,
      "cardinality": 11,
      "distance": 0.05722008983377925,
      "idx": 50
    },
    {
      "cId1": 12,
      "cId2": 76,
      "cardinality": 2,
      "distance": 0.05759939999999997,
      "idx": 51
    },
    {
      "cId1": 125,
      "cId2": 145,
      "cardinality": 11,
      "distance": 0.06428312822320197,
      "idx": 52
    },
    {
      "cId1": 128,
      "cId2": 146,
      "cardinality": 13,
      "distance": 0.07560480511849083,
      "idx": 53
    },
    {
      "cId1": 38,
      "cId2": 144,
      "cardinality": 3,
      "distance": 0.10388575162674299,
      "idx": 54
    },
    {
      "cId1": 134,
      "cId2": 143,
      "cardinality": 7,
      "distance": 0.10934950232551778,
      "idx": 55
    },
    {
      "cId1": 135,
      "cId2": 148,
      "cardinality": 4,
      "distance": 0.1246110733829239,
      "idx": 56
    },
    {
      "cId1": 133,
      "cId2": 147,
      "cardinality": 7,
      "distance": 0.12867245336592886,
      "idx": 57
    },
    {
      "cId1": 121,
      "cId2": 130,
      "cardinality": 5,
      "distance": 0.13408951945061243,
      "idx": 58
    },
    {
      "cId1": 139,
      "cId2": 151,
      "cardinality": 17,
      "distance": 0.15317533243443784,
      "idx": 59
    },
    {
      "cId1": 24,
      "cId2": 27,
      "cardinality": 2,
      "distance": 0.15473251,
      "idx": 60
    },
    {
      "cId1": 149,
      "cId2": 152,
      "cardinality": 24,
      "distance": 0.21470334616370854,
      "idx": 61
    },
    {
      "cId1": 32,
      "cId2": 154,
      "cardinality": 8,
      "distance": 0.2744583200103619,
      "idx": 62
    },
    {
      "cId1": 153,
      "cId2": 157,
      "cardinality": 8,
      "distance": 0.3082709894753391,
      "idx": 63
    },
    {
      "cId1": 158,
      "cId2": 160,
      "cardinality": 41,
      "distance": 0.44575581657583757,
      "idx": 64
    },
    {
      "cId1": 155,
      "cId2": 156,
      "cardinality": 11,
      "distance": 0.44979172617571433,
      "idx": 65
    },
    {
      "cId1": 28,
      "cId2": 159,
      "cardinality": 3,
      "distance": 0.5001272169468693,
      "idx": 66
    },
    {
      "cId1": 161,
      "cId2": 163,
      "cardinality": 49,
      "distance": 0.7485971860497874,
      "idx": 67
    },
    {
      "cId1": 11,
      "cId2": 150,
      "cardinality": 3,
      "distance": 0.8268313310314396,
      "idx": 68
    },
    {
      "cId1": 162,
      "cId2": 166,
      "cardinality": 57,
      "distance": 0.9867039481620183,
      "idx": 69
    },
    {
      "cId1": 54,
      "cId2": 77,
      "cardinality": 2,
      "distance": 1.0,
      "idx": 70
    },
    {
      "cId1": 167,
      "cId2": 169,
      "cardinality": 5,
      "distance": 1.2349951026857424,
      "idx": 71
    },
    {
      "cId1": 164,
      "cId2": 165,
      "cardinality": 14,
      "distance": 1.3348710366854548,
      "idx": 72
    },
    {
      "cId1": 170,
      "cId2": 171,
      "cardinality": 19,
      "distance": 2.086742379842595,
      "idx": 73
    },
    {
      "cId1": 168,
      "cId2": 172,
      "cardinality": 76,
      "distance": 3.3002644372620455,
      "idx": 74
    },
    {
      "cId1": 94,
      "cId2": 173,
      "cardinality": 77,
      "distance": null,
      "idx": 75
    },
  ]
}

function getHierarchyRoot(data) {
  const {hierarchy: h, n: nLeafs} = data;
  const nClusters = h.length;
  console.log("nLeafs:", nLeafs, "nClusters:", nClusters);
  const tree = Array(nLeafs + nClusters);

  // add leafs
  for (let i = 0; i < nLeafs; i++) {
    tree[i] = {id: i, distance: 0.0, size: 1, children: []};
  }

  // add clusters
  for (let i = 0; i < nClusters; i++) {
    const node = h[i];
    const idx = nLeafs + i;
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
  return tree[tree.length - 1];
}

function drawChart(id, maxDistance, tree, root, innerWidth, innerHeight, x0) {
  const svg = d3.select("#" + id);

  const xScale = d3.scaleLinear()
    .domain([maxDistance, 0])
    .range([0, innerWidth]);
  const yScale = d3.scaleLinear()
    .domain([1, 0])
    .range([0, innerHeight]);

  // const yAxis = svg.call(d3.axisLeft(yScale))
  const xAxis = d3.select(`#${id}-axis`)
    .transition().duration(100)
    .attr("transform", `translate(0,${x0})`)
    .call(d3.axisTop(xScale));
  const t = d3.transition().duration(100);

  const link = d3.select(`#${id}-links`).selectAll()
    .data(root.links())
    .join("path")
    .transition(t)
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

  node.transition(t)
    .attr("stroke-linejoin", "round")
    .attr("stroke-width", 3)
    .attr("transform", d => `translate(${d.y},${d.x})`);

  node.append("circle")
    .transition(t)
    .attr("fill", d => d.children ? "#555" : "#999")
    .attr("r", 2.5);

  node.append("text")
    .transition(t)
    .attr("fill", "black")
    .attr("dy", "0.31em")
    .attr("x", d => d.children ? -6 : 6)
    .attr("text-anchor", d => d.children ? "end" : "start")
    .text(d => d.data.id)
    .attr("stroke", "white")
    .attr("paint-order", "stroke");
}

function D3Dendrogram({data}) {
  let height = 500;
  const width = 928;
  // const margin = {top: 20, right: 20, bottom: 30, left: 40};
  const margin = {top: 0, right: 0, bottom: 0, left: 0};
  let innerHeight = height - margin.top - margin.bottom;
  const innerWidth = width - margin.left - margin.right - 10;
  const dx = 10;
  let x0 = 0;
  let viewPort = "0,0,0,0";
  const id = useId().replaceAll(":", "");

  const hierarchy = getHierarchyRoot(data);
  const maxDistance = Math.max(...data.hierarchy.map(obj => obj.distance), 0);
  const hasData = hierarchy && hierarchy.children && hierarchy.children.length > 0;
  let root = {};
  let tree = {};
  console.log(hierarchy);

  if(hasData) {
    root = d3.hierarchy(hierarchy);
    const dy = innerWidth / (root.height + 1);
    console.log("Hierarchy root:", root);

    root.sort((a, b) => b.height - a.height || d3.ascending(a.id, b.id));
    tree = d3.cluster().nodeSize([dx, dy])(root);

    // Compute the extent of the tree. Note that x and y are swapped here
    // because in the tree layout, x is the breadth, but when displayed, the
    // tree extends right rather than down.
    x0 = Infinity;
    let x1 = -x0;
    root.each(d => {
      if (d.x > x1) x1 = d.x;
      if (d.x < x0) x0 = d.x;
    });
    height = x1 - x0 + dx * 2;
    console.log("height", height, "x1", x1, "x0", x0, "dx", dx);
    innerHeight = height - margin.top - margin.bottom;
    viewPort = [-dy / 3, x0 - dx, innerWidth + 10, innerHeight]
      .toString()
      .replaceAll("[", "")
      .replaceAll("]", "");
  }


  useEffect(() => {
    if (hasData)
      drawChart(id, maxDistance, tree, root, innerWidth, innerHeight, x0);
  }, [maxDistance, tree, root, innerWidth, innerHeight, x0]);

  return (
    <div>
      <svg width={width} height={height}
           viewBox={viewPort}>
        <g id={id} transform={"translate(" + margin.left + "," + margin.top + ")"}>
          <g id={`${id}-axis`}/>
          <g id={`${id}-links`}/>
          <g id={`${id}-nodes`}/>
        </g>
      </svg>
    </div>
  );
}

export default D3Dendrogram;
