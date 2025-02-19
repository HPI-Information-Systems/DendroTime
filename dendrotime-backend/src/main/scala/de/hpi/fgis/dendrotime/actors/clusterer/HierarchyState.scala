package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.BloomFilterOptions
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.model.StateModel.ClusteringState
import de.hpi.fgis.dendrotime.structures.{QualityTrace, HierarchySimilarityConfig as HSC}

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
               clusterQualityMethod: Option[String]
              )(using BloomFilterOptions): HierarchyState = {

    new TrackingHierarchyState(
      n, Hierarchy.empty, hierarchySimilarityConfig, hierarchyQualityConfig, clusterQualityMethod
    )
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
                                       hierarchySimilarityConfig: Option[HSC],
                                       hierarchyQualityConfig: Option[HSC],
                                       clusterQualityMethod: Option[String]
                                      )(using BloomFilterOptions) extends HierarchyState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyMetricOps.given

    private var ops: Int = 0
    private var cumsum: Double = 0.0
    private val hierarchySimilarityState = hierarchySimilarityConfig.map(HierarchySimilarityState(_, n))
    private var hierarchyQualityState: Option[HierarchyQualityState] = None
    private var gtClasses: Option[Array[Int]] = None
    private val traceBuilder: QualityTrace.QualityTraceBuilder = QualityTrace.newBuilder

    override def computations: Int = ops

    override def toClusteringState: ClusteringState = ClusteringState(currentHierarchy, traceBuilder.result())

    override def setGtHierarchy(hierarchy: Option[Hierarchy]): Unit = {
      hierarchyQualityState = hierarchy.zip(hierarchyQualityConfig).map{ (h, c) =>
        require(n == h.n, s"N does not match ($n != ${h.n})!")
        HierarchyQualityState(c, h)
      }
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
        case Some(state) => 1.0 - state.computeSimilarity(hierarchy)
        case None => 1.0
      }
      cumsum += similarity
      currentHierarchy = hierarchy
      traceBuilder.addStep(index, cumsum)
      ops += 1

      hierarchyQualityState.foreach{ state =>
        val gtSimilarity = state.computeQuality(hierarchy)
        traceBuilder.withGtSimilarity(gtSimilarity)
      }

      gtClasses.foreach{ classes =>
        clusterQualityMethod match {
          case Some("ari") =>
            val clusterQuality = currentHierarchy.ari(classes)
            traceBuilder.withClusterQuality(clusterQuality)

          case Some("ami") =>
            throw new IllegalArgumentException("AMI is not supported yet!")
//            val clusterQuality = currentHierarchy.ami(classes)
//            traceBuilder.withClusterQuality(clusterQuality)

          case Some(other) =>
            throw new IllegalArgumentException(s"Unsupported cluster quality method: $other")

          case None =>
          // ignore
        }
      }
    }

    override def dispose(): Unit = {
      hierarchySimilarityState.foreach(_.dispose())
      hierarchyQualityState.foreach(_.dispose())
    }
  }
}
