package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy}
import de.hpi.fgis.dendrotime.model.StateModel.{ClusteringState, QualityTrace}
import de.hpi.fgis.dendrotime.structures.{HierarchyWithBF, HierarchyWithBitset, HierarchySimilarityConfig as HSC}

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

  def tracking(n: Int,
               hierarchySimilarityConfig: Option[HSC],
               hierarchyQualityConfig: Option[HSC],
               clusterQualityMethod: Option[String])(using bfOptions: BloomFilterOptions): HierarchyState = {

    val hierarchySimilarityState = hierarchySimilarityConfig.map(HierarchySimilarityState(_, n))
    val hierarchyQualityState = hierarchyQualityConfig.map(HierarchySimilarityState(_, n))
    new TrackingHierarchyState(n, Hierarchy.empty, hierarchySimilarityState, hierarchyQualityState, clusterQualityMethod)
  }

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

  private class TrackingHierarchyState(override val n: Int,
                                       private var currentHierarchy: Hierarchy,
                                       hierarchySimilarityState: Option[HierarchySimilarityState],
                                       hierarchyQualityState: Option[HierarchySimilarityState],
                                       clusterQualityMethod: Option[String]) extends HierarchyState {

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
      gtClasses = classes.map { c =>
        val mapping = c.iterator.zipWithIndex.toMap
        c.map(mapping.apply)
      }
    }

    override def newHierarchy(index: Int, hierarchy: Hierarchy): Unit = {
      ops += 1
      val similarity = hierarchySimilarityState match {
        case Some(state) => state.computeSimilarity(hierarchy)
        case None => 0.0
      }
      cumsum += similarity
      currentHierarchy = hierarchy
      traceBuilder.addStep(index, cumsum)
      ops += 1

      if gtHierarchy.isDefined && hierarchyQualityState.isDefined then
        val gtSimilarity = hierarchyQualityState.get.computeSimilarity(Hierarchy.empty, gtHierarchy)
        traceBuilder.withGtSimilarity(gtSimilarity)

      if gtClasses.isDefined then
        clusterQualityMethod match {
          case Some("ari") =>
            val clusterQuality = currentHierarchy.ari(gtClasses.get)
            traceBuilder.withClusterQuality(clusterQuality)

          case Some("ami") =>
            val clusterQuality = currentHierarchy.ami(gtClasses.get)
            traceBuilder.withClusterQuality(clusterQuality)

          case Some(other) =>
            throw new IllegalArgumentException(s"Unsupported cluster quality method: $other")

          case None =>
            // ignore
        }
    }

    override def dispose(): Unit = {
      hierarchySimilarityState.foreach(_.dispose())
      hierarchyQualityState.foreach(_.dispose())
    }
  }
}
