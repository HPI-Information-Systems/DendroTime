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

  private def jaccardSimilarity[T](s1: scala.collection.Set[T], s2: scala.collection.Set[T]): Double = {
    val intersection = s1 & s2
    val union = s1 | s2
    intersection.size.toDouble / union.size
  }
}

case class HierarchyWithClusters(hierarchy: Hierarchy, clusters: Set[BitSet]) {
  def similarity(other: HierarchyWithClusters): Double =
    HierarchyWithClusters.jaccardSimilarity(clusters, other.clusters)
}
