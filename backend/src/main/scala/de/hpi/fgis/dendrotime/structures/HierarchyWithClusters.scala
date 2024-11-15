package de.hpi.fgis.dendrotime.structures

import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy

import scala.collection.{BitSet, mutable}


object HierarchyWithClusters {
  def apply(h: Hierarchy): HierarchyWithClusters = {
    val clusters = mutable.ArrayBuffer.tabulate(h.n)(i => BitSet(i))
    for node <- h do
      clusters += clusters(node.cId1) | clusters(node.cId2)
    HierarchyWithClusters(h, clusters.slice(h.n, h.n + h.n - 1).toSet)
  }

  given Conversion[Hierarchy, HierarchyWithClusters] = HierarchyWithClusters.apply(_)
}

case class HierarchyWithClusters(hierarchy: Hierarchy, clusters: Set[BitSet]) {
  def similarity(other: HierarchyWithClusters): Double = {
    val intersection = clusters & other.clusters
    val union = clusters | other.clusters
    intersection.size.toDouble / union.size
  }
}
