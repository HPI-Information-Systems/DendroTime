package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.BloomFilter
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy

import scala.language.implicitConversions

object HierarchyState {
  def empty(n: Int): HierarchyState = HierarchyState(
    n = n,
    currentHierarchy = Hierarchy.empty,
    prevClusters = BFClusters.empty,
    currentClusters = BFClusters.emptyBFs(n),
    initialClusters = BFClusters.initial(n),
    similarities = Seq.empty,
    computations = 0
  )

  extension (bf: BloomFilter[Int]) {
    def jaccardSimilarity(other: BloomFilter[Int]): Double = {
      val intersection = bf & other
      val union = bf | other
      intersection.approximateElementCount.toDouble / union.approximateElementCount
    }
  }

  private object BFClusters {
    def empty: BFClusters = BFClusters(Array.empty)

    def initial(n: Int): BFClusters =
      val bloomFilters = Array.tabulate(n)(i =>
        BloomFilter[Int](n + n - 1) += i
      )
      BFClusters(bloomFilters)

    def emptyBFs(n: Int): BFClusters = BFClusters(Array.fill(n)(BloomFilter[Int](n + n - 1)))
  }

  private case class BFClusters(bloomFilters: Array[BloomFilter[Int]]) {
    given Conversion[Boolean, Int] = if _ then 1 else 0

    def length: Int = bloomFilters.length

    def apply(i: Int): BloomFilter[Int] = bloomFilters(i)

    def get(i: Int): Option[BloomFilter[Int]] =
      if 0 < i && i < length then Some(bloomFilters(i)) else None

    def getOrElse(i: Int, default: () => BloomFilter[Int]): BloomFilter[Int] =
      if 0 < i && i < length then bloomFilters(i) else default()

    def jaccardSimilarity(other: BFClusters): Double =
      if length != other.length then
        return 0

      var similarity = 0.0
      for i <- 0 until length do
        similarity += bloomFilters(i).jaccardSimilarity(other.bloomFilters(i))

      similarity / length

    def equalitySimilarity(other: BFClusters): Double =
      if length != other.length then
        return 0

      var similarity = 0.0
      for i <- 0 until length do
        similarity = similarity + (bloomFilters(i) == other.bloomFilters(i))
      similarity / length
  }

}

case class HierarchyState private(n: Int,
                                  similarities: Seq[Double],
                                  computations: Int,
                                  private val currentHierarchy: Hierarchy,
                                  private val prevClusters: HierarchyState.BFClusters,
                                  private val currentClusters: HierarchyState.BFClusters,
                                  private val initialClusters: HierarchyState.BFClusters) {

  import HierarchyState.*
  import BFClusters.*

  def hierarchy: Hierarchy = currentHierarchy

  def newHierarchy(hierarchy: Hierarchy): HierarchyState =
    val prevClusters = this.currentClusters
    val (currentClusters, similarity) = computeClusterSimilarity(hierarchy)
    copy(
      similarities = similarities :+ similarity,
      computations = computations + 1,
      currentHierarchy = hierarchy,
      prevClusters = prevClusters,
      currentClusters = currentClusters
    )

  private def computeClusterSimilarity(hierarchy: Hierarchy): (BFClusters, Double) = {
    require(hierarchy.n == n, "N does not match!")
    val bfs = Array.ofDim[BloomFilter[Int]](hierarchy.length)
    var similarity = 0.0

    def getBF(i: Int): BloomFilter[Int] = if i < n then initialClusters(i) else bfs(i - n)

    for i <- 0 until hierarchy.length do
      val cid1 = hierarchy.cId1(i)
      val cid2 = hierarchy.cId2(i)
      val bf = getBF(cid1) | getBF(cid2)
      similarity += bf.jaccardSimilarity(currentClusters.bloomFilters(i))
      bfs(i) = bf
    (BFClusters(bfs), similarity / hierarchy.length)
  }
}
