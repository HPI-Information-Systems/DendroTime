//> using scala 3.3.3
//> using repository central
//> using repository ivy2local
//> using repository m2local
//> using repository https://repo.akka.io/maven
//> using dep de.hpi.fgis:progress-bar_3:0.1.0
//> using dep de.hpi.fgis:dendrotime_3:0.0.0+147-b5bbd4d6+20241126-1012
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.{DTW, Distance, MSM, SBD}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, Linkage, computeHierarchy}
import de.hpi.fgis.dendrotime.clustering.metrics.AdjustedRandScore
import de.hpi.fgis.dendrotime.io.{CSVReader, CSVWriter, TsParser}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries
import de.hpi.fgis.dendrotime.structures.*
import de.hpi.fgis.dendrotime.structures.HierarchyWithClusters.given
import de.hpi.fgis.dendrotime.structures.strategies.*
import de.hpi.fgis.dendrotime.structures.strategies.ApproxDistanceWorkGenerator.Direction
import de.hpi.fgis.progressbar.{ProgressBar, ProgressBarFormat}

import java.io.{File, PrintWriter}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Random, Using}

extension (order: Array[(Int, Int)])
  def toCsvRecord: String = order.map(t => s"(${t._1},${t._2})").mkString("\"", " ", "\"")

extension (hierarchy: Hierarchy) {
  def quality(classes: Array[Int], nClasses: Int): Double = {
    val clusters = CutTree(hierarchy, nClasses)
    AdjustedRandScore(classes, clusters)
  }
}

class GtBestTsOrderStrategy(tsOrder: Array[Int]) extends WorkGenerator[Int] with FCFSMixin[Int] {
  override protected val tsIds: IndexedSeq[Int] = tsOrder
}

def writeStrategiesToCsv(strategies: Map[String, (Int, Double, Long, Array[(Int, Int)])], filename: String): Unit = {
  val data = strategies.map { case (name, (index, auc, duration, order)) =>
    s"$name,$index,$auc,$duration,${order.toCsvRecord}"
  }
  val header = "strategy,index,quality,time_ms,order"
  val content = Seq(header) ++ data
  Using.resource(new PrintWriter(new File(filename), "UTF-8")){ writer =>
    content.foreach(writer.println)
  }
}

def computeApproxDistances(distance: Distance, ts: Array[LabeledTimeSeries], n: Int, snippetSize: Int = 20): PDist = {
  val approxDists = PDist.empty(n).mutable
  var i = 0
  var j = 1
  while i < n - 1 && j < n do
    val ts1 = ts(i)
    val ts2 = ts(j)
    val scale = Math.max(ts1.data.length, ts2.data.length) / snippetSize
    val ts1Center = ts1.data.length / 2
    val ts2Center = ts2.data.length / 2
    approxDists(i, j) = distance(
      ts1.data.slice(ts1Center - snippetSize / 2, ts1Center + snippetSize / 2),
      ts2.data.slice(ts2Center - snippetSize / 2, ts2Center + snippetSize / 2)
    ) * scale
    j += 1
    if j == n then
      i += 1
      j = i + 1
  approxDists
}

// parse options
@tailrec
def parseOptions(args: List[String], required: List[String], optional: List[String], parsed: Map[String, String] = Map.empty): Map[String, String] = args match {
  case Nil =>
    parsed
  case "--help" :: _ =>
    println(usage)
    sys.exit(0)
  case "--" :: _ =>
    parsed
  case key :: value :: tail if key.startsWith("--") && optional.contains(key) =>
    parseOptions(tail, required, optional, parsed + (key.drop(2) -> value))
  case value :: tail if required.nonEmpty =>
    parseOptions(tail, required.tail, optional, parsed + (required.head -> value))
  case _ =>
    println(s"Invalid arguments: ${args.mkString(", ")}\n" + usage)
    sys.exit(1)
}

val folder: String = new File(scriptPath).getCanonicalFile.getParentFile.getParent
val options: Map[String, String] = parseOptions(
  args = args.toList,
  required = List("dataset"),
  optional = List("--resultFolder", "--dataFolder", "--qualityMeasure"),
  parsed = Map(
    "resultFolder" -> s"$folder/experiments/best-ts-order/",
    "dataFolder" -> s"$folder/data/datasets/",
    "qualityMeasure" -> "ari"
  )
)
val usage = "Usage: script <dataset> --resultFolder <resultFolder> --dataFolder <dataFolder> --qualityMeasure <hierarchy|ari>"

///////////////////////////////////////////////////////////
val distanceName = "msm"
val linkageName = "ward"
val distance = distanceName match {
  case "msm" => MSM(c = 0.5, window = 0.05, itakuraMaxSlope = Double.NaN)
  case "dtw" => DTW(window = 0.05, itakuraMaxSlope = Double.NaN)
  case "sbd" => SBD(standardize = false)
  case s => throw new IllegalArgumentException(s"Unknown distance metric: $s")
}
val linkage = Linkage(linkageName)
val dataset = options("dataset")
val qualityMeasure = options("qualityMeasure").toLowerCase.strip
val inputDataFolder = {
  val f = options("dataFolder")
  if !f.endsWith("/") then f + "/" else f
}
val resultFolder = {
  val f = options("resultFolder")
  if !f.endsWith("/") then f + "-" + qualityMeasure + "/"
  else f.stripSuffix("/") + "-" + qualityMeasure + "/"
}
val maxHierarchySimilarities = 1000
///////////////////////////////////////////////////////////
val datasetTrainFile = {
  val f = new File(inputDataFolder + s"$dataset/${dataset}_TRAIN.ts")
  if f.exists() then Some(f) else None
}
val datasetTestFile = new File(inputDataFolder + s"$dataset/${dataset}_TEST.ts")
val bestOrderFile = new File(resultFolder + s"best-ts-order-$dataset.csv")

println(s"Processing dataset: $dataset")
println("Configuration:")
println(s"  inputDataFolder = $inputDataFolder")
println(s"  resultFolder = $resultFolder")
println(s"  bestOrderFile = $bestOrderFile")
println(s"  distance = $distance")
println(s"  linkage = $linkage")
println(s"  maxHierarchySimilarities = $maxHierarchySimilarities")
println(s"  qualityMeasure = $qualityMeasure")
println()

// load time series
println("Loading time series ...")
val trainTimeseries = datasetTrainFile.fold(Array.empty[LabeledTimeSeries])(f => TsParser.loadAllLabeledTimeSeries(f))
val timeseries = trainTimeseries ++ TsParser.loadAllLabeledTimeSeries(datasetTestFile, idOffset = trainTimeseries.length)
val n = timeseries.length
val m = n * (n - 1) / 2
val classes = timeseries.map(_.label.toInt)
val nClasses = classes.distinct.length
val hierarchyCalcFactor = Math.floorDiv(m, Math.min(maxHierarchySimilarities, m))

println("Computing pairwise distances ...")
val t0 = System.nanoTime()
val approxDists = computeApproxDistances(distance, timeseries, n)
val dists = PDist(distance.pairwise(timeseries.map(_.data)), n)
println(s"... done in ${(System.nanoTime() - t0) / 1_000_000} ms")

// prepare ground truth
val approxHierarchy = computeHierarchy(approxDists, linkage)
val targetHierarchy = computeHierarchy(dists, linkage)

def executeStaticStrategy(strategy: Iterator[(Int, Int)]): (Array[(Int, Int)], Array[Double]) = {
  val order = Array.ofDim[(Int, Int)](m)
  val similarities = mutable.ArrayBuilder.make[Double]
  similarities.sizeHint(maxHierarchySimilarities + 2)
  val wDists = approxDists.mutableCopy
  similarities += (
    if qualityMeasure == "hierarchy" then approxHierarchy.similarity(targetHierarchy)
    else approxHierarchy.quality(classes, nClasses)
    )

  var k = 0
  while strategy.hasNext do
    val (i, j) = strategy.next()
    wDists(i, j) = dists(i, j)
    val hierarchy = computeHierarchy(wDists, linkage)
    order(k) = (i, j)
    if k % hierarchyCalcFactor == 0 || k == m-1 then
      similarities += (
        if qualityMeasure == "hierarchy" then hierarchy.similarity(targetHierarchy)
        else hierarchy.quality(classes, nClasses)
        )
    k += 1

  order -> similarities.result()
}
// load TS order
println("Loading best TS order ...")
val bestTsOrder = CSVReader.parse[Double](bestOrderFile)
  .map(a => (a(0).toInt, a(1)))
  .sortBy(_._2)
  .map(_._1)
println(s"  best TS order: ${bestTsOrder.mkString(", ")}")
println("... done.")

// compute ordering
println(s"Executing best TS order strategy for dataset $dataset with $n time series")
println(s"  n time series = $n")
println(s"  n pairs = ${n*(n-1)/2}")

// execute all defined strategies
val (order, qualities) = executeStaticStrategy(GtBestTsOrderStrategy(bestTsOrder))
println(s"  order = ${order.toCsvRecord}")
val auc = qualities.sum / qualities.length
println(f"  AUC = $auc%.4f")
println()

println(s"Computed qualities for all orderings, storing to CSVs ...")
val results = Map(
  "gtBestTsOrder" -> (0, auc, 0L, order)
)
writeStrategiesToCsv(results, resultFolder + s"strategies-$n-$dataset.csv")
CSVWriter.write(resultFolder + s"traces-$n-$dataset.csv", Array(qualities))
println("Done!")
