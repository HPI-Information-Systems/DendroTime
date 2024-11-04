package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}
import de.hpi.fgis.dendrotime.actors.clusterer.ClusterSimilarityOptions.Similarity
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy}
import de.hpi.fgis.dendrotime.clustering.metrics.AdjustedRandScore
import de.hpi.fgis.dendrotime.model.StateModel.{ClusteringState, QualityTrace}

import scala.collection.mutable
import scala.language.implicitConversions

trait HierarchyState {
  def n: Int

  def computations: Int

  def toClusteringState: ClusteringState

  def setGtHierarchy(hierarchy: Option[Hierarchy]): Unit

  def setGtClasses(classes: Option[Array[String]]): Unit

  def newHierarchy(index: Int, hierarchy: Hierarchy): Unit

  def dispose(): Unit
}

object HierarchyState {
  def nonTracking(n: Int): HierarchyState = new NonTrackingHierarchyState(n, Hierarchy.empty)

  def tracking(n: Int)(using options: ClusterSimilarityOptions): HierarchyState = {
    given BloomFilterOptions = options.bfOptions

    new QualityTrackingHierarchyState(
      n = n,
      initialClusters = initialBFs(n),
      currentHierarchy = HierarchyWithBF.emptyBFs(n),
    )
  }

  private def initialBFs(n: Int)(using BloomFilterOptions): Array[BloomFilter[Int]] =
    Array.tabulate(n)(i =>
      BloomFilter[Int](n + n - 1) += i
    )

  private class NonTrackingHierarchyState(override val n: Int,
                                          private var currentHierarchy: Hierarchy) extends HierarchyState {
    private var ops: Int = 0

    override def computations: Int = ops

    override def toClusteringState: ClusteringState = ClusteringState(currentHierarchy, QualityTrace.empty)

    override def setGtHierarchy(hierarchy: Option[Hierarchy]): Unit = ()

    override def setGtClasses(classes: Option[Array[String]]): Unit = ()

    override def newHierarchy(index: Int, hierarchy: Hierarchy): Unit = {
      ops += 1
      currentHierarchy = hierarchy
    }

    override def dispose(): Unit = ()
  }

  private class QualityTrackingHierarchyState(override val n: Int,
                                              private val initialClusters: Array[BloomFilter[Int]],
                                              private var currentHierarchy: HierarchyWithBF,
                                             )(using options: ClusterSimilarityOptions) extends HierarchyState {

    import HierarchyWithBF.*

    private var ops: Int = 0
    private var gtHierarchy: Option[HierarchyWithBF] = None
    private var gtClasses: Option[Array[String]] = None
    private val traceBuilder: QualityTrace.QualityTraceBuilder = QualityTrace.newBuilder

    override def computations: Int = ops

    override def toClusteringState: ClusteringState =
      ClusteringState(currentHierarchy.hierarchy, traceBuilder.result())

    override def setGtHierarchy(hierarchy: Option[Hierarchy]): Unit = {
      hierarchy.foreach(h => require(n == h.n, "N does not match!"))

      given BloomFilterOptions = options.bfOptions

      gtHierarchy = hierarchy.map(h => HierarchyWithBF.fromHierarchy(h, initialClusters))
    }

    override def setGtClasses(classes: Option[Array[String]]): Unit = {
      gtClasses = classes
    }

    override def newHierarchy(index: Int, hierarchy: Hierarchy): Unit = {
      val (newClusters, similarity) = options.similarity match {
        case Similarity.SetJaccardSimilarity => computeClusterSimilaritySetJaccard(hierarchy)
        case _ => computeClusterSimilarityLevelwise(hierarchy)
      }
      currentHierarchy.dispose()
      currentHierarchy = newClusters
      traceBuilder.addStep(index, similarity)
      ops += 1

      if gtHierarchy.isDefined then
        val gtSimilarity = computeGtHierarchySimilarity(gtHierarchy.get)
        traceBuilder.withGtSimilarity(gtSimilarity)

      if gtClasses.isDefined then
        val clusterQuality = computeClusterQuality(gtClasses.get)
        traceBuilder.withClusterQuality(clusterQuality)
    }

    override def dispose(): Unit = {
      initialClusters.foreach(_.dispose())
      currentHierarchy.dispose()
      gtHierarchy.foreach(_.dispose())
    }

    private def computeClusterSimilarityLevelwise(hierarchy: Hierarchy): (HierarchyWithBF, Double) = {
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
          sims(j) = options.similarity(bf, currentHierarchy.bloomFilters(i))
          j += 1
        bfs(i) = bf
      (HierarchyWithBF(hierarchy, bfs), options.aggregation(Array.copyOf(sims, j), Array.copyOf(cards, j)))
    }

    private def computeClusterSimilaritySetJaccard(hierarchy: Hierarchy): (HierarchyWithBF, Double) = {
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
          previous.add(currentHierarchy.bloomFilters(i))
          current.add(bfs(i))

      val similarity = (previous & current).size.toDouble / (previous | current).size
      (HierarchyWithBF(hierarchy, bfs), similarity)
    }

    private def computeGtHierarchySimilarity(hierarchy: HierarchyWithBF): Double = {
      // TODO: also filter by cardinality bounds?
      val gt = hierarchy.bloomFilters.toSet
      val current = currentHierarchy.bloomFilters.toSet
      (gt & current).size.toDouble / (gt | current).size
    }

    private def computeClusterQuality(classes: Array[String]): Double = {
      // 1. cut tree according to k = |distinct classes|
      val nClusters = classes.distinct.length
      val clusters = CutTree(currentHierarchy.hierarchy, nClusters)
      // 2. assign arbitrary class names (strings) to the clusters
      val clusterLabels = clusters.map(_.toString)
      // 3. use adjusted_rand_score to compare the clustering with the ground truth
      AdjustedRandScore(classes, clusterLabels)
    }
  }
}
