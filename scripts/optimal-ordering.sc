//> using target.scala "3"
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.{Distance, MSM, SBD}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{Hierarchy, Linkage, computeHierarchy}
import de.hpi.fgis.dendrotime.io.{CSVWriter, TsParser}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.{LabeledTimeSeries, TimeSeries}

import java.io.File
import scala.Conversion
import scala.collection.{BitSet, mutable}
import scala.language.implicitConversions
import scala.util.Random

object HierarchyWithClusters {
  def create(h: Hierarchy): HierarchyWithClusters = {
    val clusters = mutable.ArrayBuffer.tabulate(h.n)(i => BitSet(i))
    for node <- h do
      clusters += clusters(node.cId1) | clusters(node.cId2)
    HierarchyWithClusters(h, clusters.slice(h.n, h.n + h.n - 1).toSet)
  }
}

case class HierarchyWithClusters(hierarchy: Hierarchy, clusters: Set[BitSet]) {
  def similarity(other: HierarchyWithClusters): Double = {
    val intersection = clusters & other.clusters
    val union = clusters | other.clusters
    intersection.size.toDouble / union.size
  }
}
given Conversion[Hierarchy, HierarchyWithClusters] = HierarchyWithClusters.create(_)

extension (n: Int)
  // do not use in production! (limited to int)
  def factorial: Int = {
    var result = n
    var i = n-1
    while i > 1 do
      result *= i
      i -= 1
    result
  }

def loadTimeSeries(file: File, maxTimeseries: Option[Int] = None): Array[LabeledTimeSeries] = {
  val parser = TsParser(TsParser.TsParserSettings(
    parseMetadata = false,
    tsLimit = maxTimeseries
  ))
  var idGen = 0L
  var idx = 0
  val builder = mutable.ArrayBuilder.make[LabeledTimeSeries]
  val processor = new TsParser.TsProcessor {
    override def processUnivariate(data: Array[Double], label: String): Unit = {
      val ts = LabeledTimeSeries(idGen, idx, data, label)
      builder += ts
      idGen += 1
      idx += 1
    }
  }
  parser.parse(file, processor)
  builder.result()
}

def computeDistances(file: File, n: Int, distance: Distance): (PDist, PDist) = {
  val ts = loadTimeSeries(file, Some(n))

  val approxDists = PDist.empty(n).mutable
  val dists = PDist.empty(n).mutable
  for i <- 0 until n do
    for j <- i + 1 until n do
      approxDists(i, j) = distance(ts(i).data.slice(0, 10), ts(j).data.slice(0, 10))
      dists(i, j) = distance(ts(i).data, ts(j).data)
  (approxDists, dists)
}

val n = 5
val distance = MSM(window = 0.05)
val linkage = Linkage.WardLinkage
val inputDataFolder = "Documents/projects/DendroTime/data/datasets/"
val resultFolder = "Documents/projects/DendroTime/"

// compute approx and full distances
var (approxDists, dists) = computeDistances(
  new File(inputDataFolder + "Coffee/Coffee_TEST.ts"), n, distance
)
// prepare ground truth
var approxHierarchy = computeHierarchy(approxDists, linkage)
var targetHierarchy = computeHierarchy(dists, linkage)

def executeOrdering(order: IterableOnce[(Int, Int)]): Array[Double] = {
  val similarities = mutable.ArrayBuilder.make[Double]
  similarities.sizeHint(n * (n - 1) / 2 + 1)
  val wDists = approxDists.mutableCopy
  similarities += approxHierarchy.similarity(targetHierarchy)
  for (i, j) <- order do
    wDists(i, j) = dists(i, j)
    val hierarchy = computeHierarchy(wDists, linkage)
    similarities += hierarchy.similarity(targetHierarchy)

  similarities.result()
}

// compute all orderings
val fcfs = (1 until n).flatMap(j => (0 until j).map(i => i -> j)).toArray
println(s"Computing all orderings for $n time series")
println(s"  n time series = $n")
println(s"  n pairs = ${n*(n-1)/2}")
println(s"  n pair orderings = ${(n*(n-1)/2).factorial}")
println()

var t0 = System.nanoTime()
var allOrderings = fcfs.permutations.toArray
var t1 = System.nanoTime()
println(s"Materialized all orderings in ${(t1 - t0) / 1e9} seconds, storing to CSV ...")
CSVWriter.write(
  resultFolder + "orderings.csv",
  allOrderings.map(_.flatMap(t => Array(t._1, t._2)))
)

System.gc()
println("Computing similarities for all orderings")
val total = allOrderings.length
val traces = mutable.ArrayBuilder.make[Array[Double]]
t0 = System.nanoTime()
for order <- allOrderings do
  traces += executeOrdering(order)
  if traces.length % 100_000 == 0 then
    val progress = traces.length.toDouble / total
    println(f"Progress: $progress%.2f")

t1 = System.nanoTime()

approxDists = null
dists = null
approxHierarchy = null
targetHierarchy = null
allOrderings = null
System.gc()

val tracesArray = traces.result()
println(s"Computed similarities for all orderings in ${(t1 - t0) / 1e9} seconds, storing to CSV ...")
CSVWriter.write(resultFolder + "traces.csv", tracesArray)
println("Done!")
