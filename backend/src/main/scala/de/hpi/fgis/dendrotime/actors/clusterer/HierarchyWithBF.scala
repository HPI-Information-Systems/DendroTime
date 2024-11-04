package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy


private[clusterer] object HierarchyWithBF {
  def empty: HierarchyWithBF = HierarchyWithBF(Hierarchy.empty, Array.empty)

  def emptyBFs(n: Int)(using BloomFilterOptions): HierarchyWithBF =
    HierarchyWithBF(Hierarchy.empty, Array.fill(n)(BloomFilter[Int](n + n - 1)))

  def fromHierarchy(hierarchy: Hierarchy, initialClusters: Array[BloomFilter[Int]])
                   (using BloomFilterOptions): HierarchyWithBF = {
    val bfs = Array.ofDim[BloomFilter[Int]](hierarchy.length)

    def getBF(i: Int): BloomFilter[Int] =
      if i < hierarchy.n then initialClusters(i)
      else bfs(i - hierarchy.n)

    for i <- 0 until hierarchy.length do
      val cid1 = hierarchy.cId1(i)
      val cid2 = hierarchy.cId2(i)
      bfs(i) = getBF(cid1) | getBF(cid2)
    HierarchyWithBF(hierarchy, bfs)
  }
}

private[clusterer] case class HierarchyWithBF(hierarchy: Hierarchy,
                                              bloomFilters: Array[BloomFilter[Int]]) extends AutoCloseable {
  def length: Int = bloomFilters.length

  def apply(i: Int): BloomFilter[Int] = bloomFilters(i)

  def dispose(): Unit = bloomFilters.foreach(_.close())

  override def close(): Unit = dispose()
}
