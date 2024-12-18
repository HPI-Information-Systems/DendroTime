package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}
import de.hpi.fgis.dendrotime.actors.clusterer.ClusterSimilarityOptions.Similarity
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy}
import de.hpi.fgis.dendrotime.model.StateModel.{ClusteringState, QualityTrace}
import de.hpi.fgis.dendrotime.structures.HierarchyWithBF

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
//    given BloomFilterOptions = options.bfOptions
//
//    new QualityTrackingHierarchyState(
//      n = n,
//      initialClusters = initialBFs(n),
//      currentHierarchy = HierarchyWithBF.emptyBFs(n),
//    )
    new SimpleTrackingHierarchyState(n, Hierarchy.empty)
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

  private class SimpleTrackingHierarchyState(override val n: Int,
                                             private var currentHierarchy: Hierarchy) extends HierarchyState {
    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyMetricOps.given

    private var ops: Int = 0
    private var cumsum: Double = 0.0
    private var gtHierarchy: Option[Array[Array[Int]]] = None
    private var gtClasses: Option[Array[Int]] = None
    private val traceBuilder: QualityTrace.QualityTraceBuilder = QualityTrace.newBuilder

    override def computations: Int = ops

    override def toClusteringState: ClusteringState = ClusteringState(currentHierarchy, traceBuilder.result())

    override def setGtHierarchy(hierarchy: Option[Hierarchy]): Unit = {
      hierarchy.foreach(h => require(n == h.n, "N does not match!"))
      gtHierarchy = hierarchy.map(h => h.indices.map(CutTree(h, _)).toArray)
    }

    override def setGtClasses(classes: Option[Array[String]]): Unit = {
      gtClasses = classes.map{ c =>
        val mapping = c.iterator.zipWithIndex.toMap
        c.map(mapping.apply)
    }
  }

    override def newHierarchy(index: Int, hierarchy: Hierarchy): Unit = {
      ops += 1
      cumsum += computeClusterSimilarity(hierarchy)
      currentHierarchy = hierarchy
      traceBuilder.addStep(index, cumsum)
      ops += 1

      if gtHierarchy.isDefined then
        val gtSimilarity = currentHierarchy.approxAverageARI(gtHierarchy.get)
        traceBuilder.withGtSimilarity(gtSimilarity)

      if gtClasses.isDefined then
        val clusterQuality = computeClusterQuality(gtClasses.get)
        traceBuilder.withClusterQuality(clusterQuality)
    }

    override def dispose(): Unit = ()

    private def computeClusterSimilarity(newHierarchy: Hierarchy): Double = {
      // use multi-cut and ARI for comparison
//      1 - newHierarchy.approxAverageARI(currentHierarchy)
      // use a single cut and just compare the labels
      currentHierarchy.labelChangesAt(newHierarchy)
    }

    private def computeClusterQuality(classes: Array[Int]): Double =
      currentHierarchy.ari(classes)
  }

  private class QualityTrackingHierarchyState(override val n: Int,
                                              private val initialClusters: Array[BloomFilter[Int]],
                                              private var currentHierarchy: HierarchyWithBF,
                                             )(using options: ClusterSimilarityOptions) extends HierarchyState {

    import HierarchyWithBF.fromHierarchy
    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBFMetricOps.given

    private var ops: Int = 0
    private var gtHierarchy: Option[Array[Array[Int]]] = None
    private var gtClasses: Option[Array[Int]] = None
    private val traceBuilder: QualityTrace.QualityTraceBuilder = QualityTrace.newBuilder

    override def computations: Int = ops

    override def toClusteringState: ClusteringState =
      ClusteringState(currentHierarchy.hierarchy, traceBuilder.result())

    override def setGtHierarchy(hierarchy: Option[Hierarchy]): Unit = {
      hierarchy.foreach(h => require(n == h.n, "N does not match!"))
      gtHierarchy = hierarchy.map(h => h.indices.map(CutTree(h, _)).toArray)
    }

    override def setGtClasses(classes: Option[Array[String]]): Unit = {
      gtClasses = classes.map{ c =>
        val mapping = c.iterator.zipWithIndex.toMap
        c.map(mapping.apply)
      }
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
        val gtSimilarity = currentHierarchy.approxAverageARI(gtHierarchy.get)
        traceBuilder.withGtSimilarity(gtSimilarity)

      if gtClasses.isDefined then
        val clusterQuality = computeClusterQuality(gtClasses.get)
        traceBuilder.withClusterQuality(clusterQuality)
    }

    override def dispose(): Unit = {
      initialClusters.foreach(_.dispose())
      currentHierarchy.dispose()
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
      // create the bloom filters once
      val newHierarchy = HierarchyWithBF.fromHierarchy(hierarchy, initialClusters)
      val similarity = newHierarchy.similarity(currentHierarchy, options.cardLowerBound, options.cardUpperBound)
      (newHierarchy, similarity)
    }

    private def computeWeightedClusterSimilarity(hierarchy: Hierarchy): (HierarchyWithBF, Double) = {
      require(hierarchy.n == n, "N does not match!")
      // create the bloom filters once
      val newHierarchy = HierarchyWithBF.fromHierarchy(hierarchy, initialClusters)
      val similarity = newHierarchy.weightedSimilarity(currentHierarchy)
      (newHierarchy, similarity)
    }

    private def computeClusterQuality(classes: Array[Int]): Double =
      currentHierarchy.ari(classes)
  }
}
