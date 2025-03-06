package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, HierarchyWithBF, HierarchyWithBitset}
import de.hpi.fgis.dendrotime.structures.HierarchySimilarityConfig as HSC

import scala.collection.BitSet
import scala.language.implicitConversions
import scala.util.Using

trait HierarchyQualityState extends AutoCloseable {
  def computeQuality(hierarchy: Hierarchy): Double

  def dispose(): Unit

  override def close(): Unit = dispose()
}

object HierarchyQualityState {
  def apply(config: HSC, gtHierarchy: Hierarchy)(using BloomFilterOptions): HierarchyQualityState = config match {
    case c: HSC.WithBf =>
      HierarchyQualityStateWithBF(c, gtHierarchy)

    case c: HSC.WithBitset =>
      HierarchyQualityStateWithBitset(c, gtHierarchy)

    case _ =>
      val cuts = CutTree(gtHierarchy, gtHierarchy.indices.toArray)
      LeanHierarchyQualityState(config, cuts, gtHierarchy.n)
  }

  private def initialBFs(n: Int)(using BloomFilterOptions): Array[BloomFilter[Int]] =
    Array.tabulate(n)(i =>
      BloomFilter[Int](n + n - 1) += i
    )

  private[clusterer] case class LeanHierarchyQualityState(
                                                           config: HSC,
                                                           targetLabels: Array[Array[Int]],
                                                           n: Int
                                                         ) extends HierarchyQualityState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyMetricOps.given

    override def computeQuality(hierarchy: Hierarchy): Double = {
      config match {
        case HSC.AriAt(k) =>
          hierarchy.ari(targetLabels(k), k)

        case HSC.AmiAt(k) =>
          hierarchy.ami(targetLabels(k), k)

        case HSC.LabelChangesAt(optionK) =>
          // use a single cut and just compare the labels
          optionK match {
            case Some(k) => hierarchy.labelChanges(targetLabels(k))
            case None => hierarchy.labelChanges(targetLabels(n / 2))
          }

        case HSC.AverageAri =>
          // use multi-cut and ARI
          hierarchy.averageARI(targetLabels)

        case HSC.ApproxAverageAri(factor) =>
          // use multi-cut and ARI with fewer cuts
          hierarchy.approxAverageARI(targetLabels, factor)

        case other =>
          throw new IllegalArgumentException(
            s"Unsupported hierarchy similarity config for LeanHierarchyQualityState: $other"
          )
      }
    }

    override def dispose(): Unit = ()
  }

  private[clusterer] case class HierarchyQualityStateWithBF(
                                                             config: HSC.WithBf,
                                                             gtHierarchy: Hierarchy,
                                                           )(
                                                             using BloomFilterOptions
                                                           ) extends HierarchyQualityState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBFMetricOps.given

    private val initialClusters: Array[BloomFilter[Int]] = initialBFs(gtHierarchy.n)
    private val targetHierarchy: HierarchyWithBF = HierarchyWithBF.fromHierarchy(gtHierarchy, initialClusters)

    override def computeQuality(hierarchy: Hierarchy): Double = config match {
      case HSC.HierarchySimilarityWithBf(cardLowerBound, cardUpperBound) =>
        Using.resource(HierarchyWithBF.fromHierarchy(hierarchy, initialClusters)) { newHierarchy =>
          newHierarchy.similarity(targetHierarchy, cardLowerBound, cardUpperBound)
        }

      case HSC.WeightedHierarchySimilarityWithBf =>
        Using.resource(HierarchyWithBF.fromHierarchy(hierarchy, initialClusters)) { newHierarchy =>
          newHierarchy.weightedSimilarity(targetHierarchy)
        }
    }

    override def dispose(): Unit = {
      initialClusters.foreach(_.dispose())
      targetHierarchy.dispose()
    }
  }

  private[clusterer] case class HierarchyQualityStateWithBitset(
                                                                 config: HSC.WithBitset,
                                                                 gtHierarchy: Hierarchy
                                                               ) extends HierarchyQualityState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBitsetMetricOps.given

    private val initialClusters: Array[BitSet] = Array.tabulate(gtHierarchy.n)(i => BitSet(i))
    private val targetHierarchy: HierarchyWithBitset = HierarchyWithBitset.fromHierarchy(gtHierarchy, initialClusters)

    override def computeQuality(hierarchy: Hierarchy): Double = config match {
      case HSC.HierarchySimilarityWithBitset(cardLowerBound, cardUpperBound) =>
        val newHierarchy = HierarchyWithBitset.fromHierarchy(hierarchy, initialClusters)
        newHierarchy.similarity(targetHierarchy, cardLowerBound, cardUpperBound)

      case HSC.WeightedHierarchySimilarityWithBitset =>
        val newHierarchy = HierarchyWithBitset.fromHierarchy(hierarchy, initialClusters)
        newHierarchy.weightedSimilarity(targetHierarchy)
    }

    override def dispose(): Unit = ()
  }
}
