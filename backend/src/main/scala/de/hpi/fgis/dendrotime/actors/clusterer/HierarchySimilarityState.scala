package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy}
import de.hpi.fgis.dendrotime.structures.{HierarchyWithBF, HierarchyWithBitset, HierarchySimilarityConfig as HSC}

trait HierarchySimilarityState {
  def computeSimilarity(hierarchy: Hierarchy, cuts: Option[Array[Array[Int]]] = None): Double

  def dispose(): Unit
}

object HierarchySimilarityState {
  def apply(config: HSC, n: Int)(using BloomFilterOptions): HierarchySimilarityState = config match {
    case c: HSC.WithBf =>
      HierarchySimilarityStateWithBF(c, initialBFs(n), HierarchyWithBF.emptyBFs(n))

    case c: HSC.WithBitset =>
      HierarchySimilarityStateWithBitset(c, HierarchyWithBitset.emptyBitsets(n))

    case _ =>
      LeanHierarchySimilarityState(config, Hierarchy.empty)
  }

  private def initialBFs(n: Int)(using BloomFilterOptions): Array[BloomFilter[Int]] =
    Array.tabulate(n)(i =>
      BloomFilter[Int](n + n - 1) += i
    )

  private[clusterer] case class LeanHierarchySimilarityState(
                                                              config: HSC,
                                                              var currentHierarchy: Hierarchy
                                                            ) extends HierarchySimilarityState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyMetricOps.given

    override def computeSimilarity(hierarchy: Hierarchy, hLabels: Option[Array[Array[Int]]] = None): Double = {
      val similarity = config match {
        case HSC.AriAt(k) =>
          hLabels match {
            case Some(cuts) => currentHierarchy.ari(cuts(k), k)
            case None => currentHierarchy.ari(CutTree(hierarchy, k), k)
          }

        case HSC.AmiAt(k) =>
          hLabels match {
            case Some(cuts) => currentHierarchy.ami(cuts(k), k)
            case None => currentHierarchy.ami(CutTree(hierarchy, k), k)
          }

        case HSC.LabelChangesAt(optionK) =>
          // use a single cut and just compare the labels
          optionK match {
            case Some(k) => currentHierarchy.labelChangesAt(hierarchy, k)
            case None => currentHierarchy.labelChangesAt(hierarchy)
          }

        case HSC.AverageAri =>
          // use multi-cut and ARI
          hLabels match {
            case Some(cuts) => currentHierarchy.averageARI(cuts)
            case None => currentHierarchy.averageARI(hierarchy)
          }

        case HSC.ApproxAverageAri(factor) =>
          // use multi-cut and ARI with fewer cuts
          hLabels match
            case Some(cuts) => currentHierarchy.approxAverageARI(cuts, factor)
            case None => currentHierarchy.approxAverageARI(hierarchy, factor)

        case other =>
          throw new IllegalArgumentException(
            s"Unsupported hierarchy similarity config for LeanHierarchySimilarityState: $other"
          )
      }
      currentHierarchy = hierarchy
      similarity
    }

    override def dispose(): Unit = ()
  }

  private[clusterer] case class HierarchySimilarityStateWithBF(
                                                                config: HSC.WithBf,
                                                                initialClusters: Array[BloomFilter[Int]],
                                                                var currentHierarchy: HierarchyWithBF,
                                                              )(
                                                                using BloomFilterOptions
                                                              ) extends HierarchySimilarityState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBFMetricOps.given

    override def computeSimilarity(hierarchy: Hierarchy, cuts: Option[Array[Array[Int]]] = None): Double = config match {
      case HSC.HierarchySimilarityWithBf(cardLowerBound, cardUpperBound) =>
        val newHierarchy = HierarchyWithBF.fromHierarchy(hierarchy, initialClusters)
        val similarity = currentHierarchy.similarity(newHierarchy, cardLowerBound, cardUpperBound)
        currentHierarchy.dispose()
        currentHierarchy = newHierarchy
        similarity

      case HSC.WeightedHierarchySimilarityWithBf =>
        val newHierarchy = HierarchyWithBF.fromHierarchy(hierarchy, initialClusters)
        val similarity = currentHierarchy.weightedSimilarity(newHierarchy)
        currentHierarchy.dispose()
        currentHierarchy = newHierarchy
        similarity
    }

    override def dispose(): Unit = {
      initialClusters.foreach(_.dispose())
      currentHierarchy.dispose()
    }
  }

  private[clusterer] case class HierarchySimilarityStateWithBitset(
                                                                    config: HSC.WithBitset,
                                                                    var currentHierarchy: HierarchyWithBitset
                                                                  ) extends HierarchySimilarityState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBitsetMetricOps.given

    override def computeSimilarity(hierarchy: Hierarchy, cuts: Option[Array[Array[Int]]] = None): Double = config match {
      case HSC.HierarchySimilarityWithBitset(cardLowerBound, cardUpperBound) =>
        val similarity = currentHierarchy.similarity(hierarchy, cardLowerBound, cardUpperBound)
        currentHierarchy = hierarchy
        similarity

      case HSC.WeightedHierarchySimilarityWithBitset =>
        val similarity = currentHierarchy.weightedSimilarity(hierarchy)
        currentHierarchy = hierarchy
        similarity
    }

    override def dispose(): Unit = ()
  }
}
