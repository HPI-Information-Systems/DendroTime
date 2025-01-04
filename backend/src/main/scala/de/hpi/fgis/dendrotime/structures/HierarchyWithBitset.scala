package de.hpi.fgis.dendrotime.structures

import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy

import scala.collection.{BitSet, mutable}


object HierarchyWithBitset {
  def empty: HierarchyWithBitset = HierarchyWithBitset(Hierarchy.empty, Array.empty)

  def emptyBitsets(n: Int): HierarchyWithBitset =
    HierarchyWithBitset(Hierarchy.empty, Array.fill(n - 1)(BitSet.empty))

  def apply(h: Hierarchy): HierarchyWithBitset = {
    val clusters = mutable.ArrayBuffer.tabulate(h.n)(i => BitSet(i))
    for node <- h do
      clusters += clusters(node.cId1) | clusters(node.cId2)
    new HierarchyWithBitset(h, clusters.slice(h.n, h.n + h.n - 1).toArray)
  }

  def fromHierarchy(hierarchy: Hierarchy, initialClusters: Array[BitSet]): HierarchyWithBitset = {
    val clusters = Array.ofDim[BitSet](hierarchy.length)

    def getBitset(i: Int): BitSet =
      if i < hierarchy.n then initialClusters(i)
      else clusters(i - hierarchy.n)

    for i <- 0 until hierarchy.length do
      val cid1 = hierarchy.cId1(i)
      val cid2 = hierarchy.cId2(i)
      clusters(i) = getBitset(cid1) | getBitset(cid2)
    HierarchyWithBitset(hierarchy, clusters)
  }

  given Conversion[Hierarchy, HierarchyWithBitset] = HierarchyWithBitset.apply(_)
}

case class HierarchyWithBitset(hierarchy: Hierarchy, clusters: Array[BitSet]) {

  def apply(i: Int): scala.collection.Set[Int] = clusters(i)

  def length: Int = clusters.length
}
