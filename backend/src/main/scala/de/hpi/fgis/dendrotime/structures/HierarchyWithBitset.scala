package de.hpi.fgis.dendrotime.structures

import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.clustering.metrics.JaccardSimilarity

import scala.collection.{BitSet, mutable}


object HierarchyWithBitset {
  def apply(h: Hierarchy): HierarchyWithBitset = {
    val clusters = mutable.ArrayBuffer.tabulate(h.n)(i => BitSet(i))
    for node <- h do
      clusters += clusters(node.cId1) | clusters(node.cId2)
    new HierarchyWithBitset(h, clusters.slice(h.n, h.n + h.n - 1).toArray)
  }

  given Conversion[Hierarchy, HierarchyWithBitset] = HierarchyWithBitset.apply(_)
}

case class HierarchyWithBitset(hierarchy: Hierarchy, clusters: Array[BitSet]) {

  def apply(i: Int): scala.collection.Set[Int] = clusters(i)

  def length: Int = clusters.length

  def similarity(other: HierarchyWithBitset): Double =
    JaccardSimilarity(clusters.toSet, other.clusters.toSet)
}
