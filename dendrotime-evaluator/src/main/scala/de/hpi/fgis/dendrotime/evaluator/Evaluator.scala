package de.hpi.fgis.dendrotime.evaluator

import org.slf4j.LoggerFactory
import de.hpi.fgis.bloomfilter.BloomFilterOptions
import de.hpi.fgis.bloomfilter.BloomFilterOptions.DEFAULT_OPTIONS
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, HierarchyWithBF, HierarchyWithBitset}
import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyMetricOps.given
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVReader

import scala.util.Using
import scala.language.implicitConversions

object Evaluator {
  def apply(args: CommonArguments): Evaluator = new Evaluator(args)

  private val logger = LoggerFactory.getLogger("DendroTime-evaluator")

}

class Evaluator private(args: CommonArguments) {
  import Evaluator.logger

  logger.debug("Loading hierarchies ...")
  private val predHierarchy = HierarchyCSVReader.parse(args.predHierarchyPath)
  private val targetHierarchy = HierarchyCSVReader.parse(args.targetHierarchyPath)
  require(
    predHierarchy.n == targetHierarchy.n,
    s"Hierarchies must have the same number of nodes (${args.predHierarchyPath} vs. ${args.targetHierarchyPath})"
  )
  private val n = predHierarchy.n
  logger.debug("... hierarchies loaded.")


  given BloomFilterOptions = DEFAULT_OPTIONS

  def ariAt(k: Int): Unit = {
    logger.info("Computing ARI at k = {}", k)
    val targetClasses = CutTree(targetHierarchy, k)
    println(predHierarchy.ari(targetClasses))
  }

  def amiAt(k: Int): Unit = {
    throw new NotImplementedError("AMI is not yet implemented!")
  }

  def labelChangesAt(k: Option[Int]): Unit = {
    logger.info("Computing labelChanges at k = {}", k)
    val result = k match {
      case Some(value) => predHierarchy.labelChangesAt(targetHierarchy, value)
      case None => predHierarchy.labelChangesAt(targetHierarchy)
    }
    println(result)
  }

  def averageAri(): Unit = {
    logger.info("Computing average ARI")
    println(predHierarchy.averageARI(targetHierarchy))
  }

  def approxAverageAri(factor: Double): Unit = {
    logger.info("Computing approxAvarageARI for factor = {}", factor)
    println(predHierarchy.approxAverageARI(targetHierarchy, factor))
  }

  def hierarchySimilarity(useBloomFilters: Boolean, cardinalityLowerBound: Int, cardinalityUpperBound: Int): Unit = {
    logger.info("Computing hierarchy similarity between {} and {} {}", cardinalityLowerBound, cardinalityUpperBound, if useBloomFilters then "with BF" else "")
    val result =
      if useBloomFilters then
        import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBFMetricOps.given

        Using.Manager { use =>
          val (predHbf, targetHbf) = createHierarchyWithBFs(predHierarchy, targetHierarchy)(using use)
          predHbf.similarity(targetHbf, cardinalityLowerBound, cardinalityUpperBound)
        }.get
      else
        predHierarchy.similarity(targetHierarchy, cardinalityLowerBound, cardinalityUpperBound)
    println(result)
  }

  def weightedHierarchySimilarity(useBloomFilters: Boolean): Unit = {
    logger.info("Computing weighted hierarchy similarity (WHS) {}", if useBloomFilters then "with BF" else "")
    val result =
      if useBloomFilters then
        import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyWithBFMetricOps.given

        Using.Manager { use =>
          val (predHbf, targetHbf) = createHierarchyWithBFs(predHierarchy, targetHierarchy)(using use)
          predHbf.weightedSimilarity(targetHbf)
        }.get
      else
        predHierarchy.weightedSimilarity(targetHierarchy)
    println(result)
  }

  private def createHierarchyWithBFs(h1: Hierarchy, h2: Hierarchy)(using use: Using.Manager): (HierarchyWithBF, HierarchyWithBF) = {
    logger.debug("Creating {} bloom filters ...", n + 2*(n-1))
    val initialBfs = use(BFHolder.initialBfs(n))
    val hbf1 = use(HierarchyWithBF.fromHierarchy(h1, initialBfs.bfs))
    val hbf2 = use(HierarchyWithBF.fromHierarchy(h2, initialBfs.bfs))
    logger.debug("... bloom filters created.")
    (hbf1, hbf2)
  }
}
