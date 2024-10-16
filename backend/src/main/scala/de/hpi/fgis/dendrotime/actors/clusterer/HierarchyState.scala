package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}
import de.hpi.fgis.dendrotime.actors.clusterer.ClusterSimilarityOptions.Similarity
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy

import scala.collection.immutable.HashSet
import scala.collection.mutable
import scala.language.implicitConversions

object HierarchyState {
  def empty(n: Int)(using options: ClusterSimilarityOptions): HierarchyState =
    given BloomFilterOptions = options.bfOptions

    HierarchyState(
      n = n,
      currentHierarchy = Hierarchy.empty,
      prevClusters = BFClusters.empty,
      currentClusters = BFClusters.emptyBFs(n),
      initialClusters = BFClusters.initial(n),
      similarities = Map.empty,
      computations = 0
    )

  private object BFClusters {
    def empty: BFClusters = BFClusters(Array.empty)

    def initial(n: Int)(using BloomFilterOptions): BFClusters =
      val bloomFilters = Array.tabulate(n)(i =>
        BloomFilter[Int](n + n - 1) += i
      )
      BFClusters(bloomFilters)

    def emptyBFs(n: Int)(using BloomFilterOptions): BFClusters =
      BFClusters(Array.fill(n)(BloomFilter[Int](n + n - 1)))
  }

  private case class BFClusters(bloomFilters: Array[BloomFilter[Int]]) {
    def length: Int = bloomFilters.length

    def apply(i: Int): BloomFilter[Int] = bloomFilters(i)
  }
}

case class HierarchyState private(n: Int,
                                  similarities: Map[Int, Double],
                                  computations: Int,
                                  private val currentHierarchy: Hierarchy,
                                  private val prevClusters: HierarchyState.BFClusters,
                                  private val currentClusters: HierarchyState.BFClusters,
                                  private val initialClusters: HierarchyState.BFClusters)
                                 (using options: ClusterSimilarityOptions) {

  import HierarchyState.*
  import BFClusters.*

  def hierarchy: Hierarchy = currentHierarchy

  def newHierarchy(index: Int, hierarchy: Hierarchy): HierarchyState =
    val prevClusters = this.currentClusters
    val (currentClusters, similarity) = options.similarity match {
      case Similarity.SetJaccardSimilarity => computeClusterSimilaritySetJaccard(hierarchy)
      case _ => computeClusterSimilarityLevelwise(hierarchy)
    }
    copy(
      similarities = similarities + (index -> similarity),
      computations = computations + 1,
      currentHierarchy = hierarchy,
      prevClusters = prevClusters,
      currentClusters = currentClusters
    )

  private def computeClusterSimilarityLevelwise(hierarchy: Hierarchy): (BFClusters, Double) = {
    require(hierarchy.n == n, "N does not match!")
    val bfs = Array.ofDim[BloomFilter[Int]](hierarchy.length)
    val sims = Array.ofDim[Double](hierarchy.length)
    val cards = Array.ofDim[Int](hierarchy.length)
    var j = 0

    def getBF(i: Int): BloomFilter[Int] = if i < n then initialClusters(i) else bfs(i - n)

    for i <- 0 until hierarchy.length do
      val cid1 = hierarchy.cId1(i)
      val cid2 = hierarchy.cId2(i)
      val bf = getBF(cid1) | getBF(cid2)
      val card = hierarchy.cardinality(i)
      if card >= options.cardLowerBound && card <= n - options.cardUpperBound then
        cards(j) = card
        sims(j) = options.similarity(bf, currentClusters.bloomFilters(i))
        j += 1
      bfs(i) = bf
    (BFClusters(bfs), options.aggregation(Array.copyOf(sims, j), Array.copyOf(cards, j)))
  }

  private def computeClusterSimilaritySetJaccard(hierarchy: Hierarchy): (BFClusters, Double) = {
    require(hierarchy.n == n, "N does not match!")
    val bfs = Array.ofDim[BloomFilter[Int]](hierarchy.length)
    val previous = mutable.HashSet.empty[BloomFilter[Int]]
    val current = mutable.HashSet.empty[BloomFilter[Int]]

    def getBF(i: Int): BloomFilter[Int] = if i < n then initialClusters(i) else bfs(i - n)

    for i <- 0 until hierarchy.length do
      val cid1 = hierarchy.cId1(i)
      val cid2 = hierarchy.cId2(i)
      bfs(i) = getBF(cid1) | getBF(cid2)
      val card = hierarchy.cardinality(i)
      if card >= options.cardLowerBound && card <= n - options.cardUpperBound then
        previous.add(currentClusters.bloomFilters(i))
        current.add(bfs(i))

    val similarity = (previous & current).size.toDouble / (previous | current).size
    (BFClusters(bfs), similarity)
  }
}
