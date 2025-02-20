package de.hpi.fgis.dendrotime.evaluator

import de.hpi.fgis.bloomfilter.BloomFilterOptions.DEFAULT_OPTIONS
import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, HierarchyWithBF, HierarchyWithBitset}
import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyMetricOps.given
import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBFMetricOps.given
import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBitsetMetricOps.given
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVReader

import scala.util.Using

object Evaluator {
  def apply(args: CommonArguments): Evaluator = new Evaluator(args)
}

class Evaluator private(args: CommonArguments) {
  private val predHierarchy = HierarchyCSVReader.parse(args.predHierarchyPath)
  private val targetHierarchy = HierarchyCSVReader.parse(args.targetHierarchyPath)
  require(predHierarchy.n == targetHierarchy.n, "Hierarchies must have the same number of nodes")
  private val n = predHierarchy.n
  given BloomFilterOptions = DEFAULT_OPTIONS

  def ariAt(k: Int): Unit = {
    Console.withOut(System.err) { println(s"Computing ARI at k = $k") }
    val targetClasses = CutTree(targetHierarchy, k)
    println(predHierarchy.ari(targetClasses))
  }

  def amiAt(k: Int): Unit = {
    throw new NotImplementedError("AMI is not yet implemented!")
  }

  def labelChangesAt(k: Option[Int]): Unit = {
    val result = k match {
      case Some(value) => predHierarchy.labelChangesAt(targetHierarchy, value)
      case None => predHierarchy.labelChangesAt(targetHierarchy)
    }
    println(result)
  }

  def averageAri(): Unit = {
    println(predHierarchy.averageARI(targetHierarchy))
  }

  def approxAverageAri(factor: Double): Unit = {
    println(predHierarchy.approxAverageARI(targetHierarchy, factor))
  }

  def hierarchySimilarity(useBloomFilters: Boolean, cardinalityLowerBound: Int, cardinalityUpperBound: Int): Unit = {
    val result =
      if useBloomFilters then
        Using.Manager { use =>
          val hierarchyBF = createHierarchyWithBF(predHierarchy)(using use)
          hierarchyBF.similarity(targetHierarchy, cardinalityLowerBound, cardinalityUpperBound)
        }.get
      else
        HierarchyWithBitset(predHierarchy).similarity(targetHierarchy, cardinalityLowerBound, cardinalityUpperBound)
    println(result)
  }

  def weightedHierarchySimilarity(useBloomFilters: Boolean): Unit = {
    val result =
      if useBloomFilters then
        Using.Manager { use =>
          val hierarchyBF = createHierarchyWithBF(predHierarchy)(using use)
          hierarchyBF.weightedSimilarity(targetHierarchy)
        }.get
      else
        HierarchyWithBitset(predHierarchy).weightedSimilarity(targetHierarchy)
    println(result)
  }

  private def createHierarchyWithBF(hierarchy: Hierarchy)(using use: Using.Manager): HierarchyWithBF = {
    val initialBfs = Array.tabulate(n)(i =>
      use(BloomFilter[Int](n + n - 1) += i)
    )
    use(HierarchyWithBF.fromHierarchy(hierarchy, initialBfs))
  }
}
