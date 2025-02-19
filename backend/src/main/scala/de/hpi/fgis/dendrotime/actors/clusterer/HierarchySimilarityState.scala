package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, HierarchyWithBF, HierarchyWithBitset}
import de.hpi.fgis.dendrotime.structures.HierarchySimilarityConfig as HSC

import scala.collection.BitSet

trait HierarchySimilarityState {
  def computeSimilarity(hierarchy: Hierarchy): Double

  def dispose(): Unit
}

object HierarchySimilarityState {
  def apply(config: HSC, n: Int)(using BloomFilterOptions): HierarchySimilarityState =
    config match {
    case c: HSC.WithBf =>
      HierarchySimilarityStateWithBF(c, n)

    case c: HSC.WithBitset =>
      HierarchySimilarityStateWithBitset(c, n)

    case _ =>
      LeanHierarchySimilarityState(config, n)
  }

  private def initialBFs(n: Int)(using BloomFilterOptions): Array[BloomFilter[Int]] =
    Array.tabulate(n)(i =>
      BloomFilter[Int](n + n - 1) += i
    )

  private[clusterer] case class LeanHierarchySimilarityState(
                                                              config: HSC, n: Int
                                                            ) extends HierarchySimilarityState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyMetricOps.given

    private var currentHierarchy: Hierarchy = Hierarchy.empty

    override def computeSimilarity(hierarchy: Hierarchy): Double = {
      val similarity = config match {
        case HSC.AriAt(k) =>
          hierarchy.ari(CutTree(currentHierarchy, k), k)

        case HSC.AmiAt(k) =>
          hierarchy.ami(CutTree(currentHierarchy, k), k)

        case HSC.LabelChangesAt(optionK) =>
          // use a single cut and just compare the labels
          optionK match {
            case Some(k) => hierarchy.labelChangesAt(currentHierarchy, k)
            case None => hierarchy.labelChangesAt(currentHierarchy)
          }

       case HSC.AverageAri =>
          // use multi-cut and ARI
          hierarchy.averageARI(currentHierarchy)

        case HSC.ApproxAverageAri(factor) =>
          // use multi-cut and ARI with fewer cuts
          hierarchy.approxAverageARI(currentHierarchy, factor)

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
                                                                config: HSC.WithBf, n: Int
                                                              )(
                                                                using BloomFilterOptions
                                                              ) extends HierarchySimilarityState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBFMetricOps.given

    private val initialClusters: Array[BloomFilter[Int]] = initialBFs(n)
    private var currentHierarchy: HierarchyWithBF = HierarchyWithBF.emptyBFs(n)

    override def computeSimilarity(hierarchy: Hierarchy): Double = config match {
      case HSC.HierarchySimilarityWithBf(cardLowerBound, cardUpperBound) =>
        val newHierarchy = HierarchyWithBF.fromHierarchy(hierarchy, initialClusters)
        val similarity = newHierarchy.similarity(currentHierarchy, cardLowerBound, cardUpperBound)
        currentHierarchy.dispose()
        currentHierarchy = newHierarchy
        similarity

      case HSC.WeightedHierarchySimilarityWithBf =>
        val newHierarchy = HierarchyWithBF.fromHierarchy(hierarchy, initialClusters)
        val similarity = newHierarchy.weightedSimilarity(currentHierarchy)
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
                                                                    config: HSC.WithBitset, n: Int
                                                                  ) extends HierarchySimilarityState {

    import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBitsetMetricOps.given

    private val initialClusters: Array[BitSet] = Array.tabulate(n)(i => BitSet(i))
    private var currentHierarchy: HierarchyWithBitset = HierarchyWithBitset.emptyBitsets(n)

    override def computeSimilarity(hierarchy: Hierarchy): Double = config match {
      case HSC.HierarchySimilarityWithBitset(cardLowerBound, cardUpperBound) =>
        val newHierarchy = HierarchyWithBitset.fromHierarchy(hierarchy, initialClusters)
        val similarity = newHierarchy.similarity(currentHierarchy, cardLowerBound, cardUpperBound)
        currentHierarchy = newHierarchy
        similarity

      case HSC.WeightedHierarchySimilarityWithBitset =>
        val newHierarchy = HierarchyWithBitset.fromHierarchy(hierarchy, initialClusters)
        val similarity = newHierarchy.weightedSimilarity(currentHierarchy)
        currentHierarchy = newHierarchy
        similarity
    }

    override def dispose(): Unit = ()
  }
}
