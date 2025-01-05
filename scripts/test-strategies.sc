//> using scala 3.3.3
//> using repository central
//> using repository ivy2local
//> using repository m2local
//> using repository https://repo.akka.io/maven
//> using dep de.hpi.fgis:progress-bar_3:0.1.0
//> using dep de.hpi.fgis:dendrotime_3:0.0.1+0-84ded2ee+20250105-1408
//> using file Strategies.sc
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.{DTW, Distance, MSM, Minkowsky, SBD}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, Linkage, computeHierarchy}
import de.hpi.fgis.dendrotime.clustering.metrics.SupervisedClustering
import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyMetricOps.given
import de.hpi.fgis.dendrotime.io.{CSVWriter, TsParser}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries
import de.hpi.fgis.dendrotime.structures.*
import de.hpi.fgis.dendrotime.structures.HierarchyWithBitset.given
import de.hpi.fgis.dendrotime.structures.strategies.*
import de.hpi.fgis.dendrotime.structures.strategies.ApproxDistanceWorkGenerator.Direction
import de.hpi.fgis.progressbar.{ProgressBar, ProgressBarFormat}

import java.io.{File, PrintWriter}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Random, Using}

import Strategies.{GtLargestPairErrorStrategy, GtLargestTsErrorStrategy}

extension (order: Array[(Int, Int)]) {
  def toCsvRecord: String = order.map(t => s"(${t._1},${t._2})").mkString("\"", " ", "\"")

  def shuffleInPlace(rng: Random): Unit = {
    var i = order.length
    while i >= 2 do
      val k = rng.nextInt(i)
      i -= 1
      val tmp = order(i)
      order(i) = order(k)
      order(k) = tmp
  }
}

extension (d: PDist) {
  def mean: Double = {
    val x = d.distances
    x.sum / (d.length - 1)
  }
  def std: Double = {
    val mean = d.mean
    val sumOfSquares = d.distances.map(x => Math.pow(x - mean, 2)).sum
    sumOfSquares / (d.length - 1)
  }
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

def computeApproxDistances(distance: Distance, ts: Array[LabeledTimeSeries], n: Int, snippetSize: Int = 20): (PDist, Option[Array[Double]]) = {
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
  approxDists -> None
}

def computeApproxDistancesTwoMean(
                                   distance: Distance,
                                   ts: Array[LabeledTimeSeries],
                                   n: Int,
                                   snippetSize: Int = 20,
                                   relOffset: Double = 0.1): (PDist, Option[Array[Double]]) = {
  val approxDists = PDist.empty(n).mutable
  val approxDiffTsError = Array.ofDim[Double](n)
  var i = 0
  var j = 1
  while i < n - 1 && j < n do
    val ts1 = ts(i)
    val ts2 = ts(j)
    val n1 = ts1.data.length
    val n2 = ts2.data.length
    val scale = Math.max(n1, n2) / snippetSize
    val o1 = (n1 * relOffset).toInt
    val o2 = (n2 * relOffset).toInt
    val begin = distance(ts1.data.slice(o1, o1 + snippetSize / 2), ts2.data.slice(o2, o2 + snippetSize / 2))
    val oe1 = (n1 * relOffset).toInt
    val oe2 = (n2 * relOffset).toInt
    val end = distance(ts1.data.slice(n1 - oe1 - snippetSize / 2, n1 - oe1), ts2.data.slice(n2 - oe2 - snippetSize / 2, n2 - oe2))
    approxDists(i, j) = (begin + end) / 2 * scale
    val error = Math.abs(begin - end)
    approxDiffTsError(i) += error
    approxDiffTsError(j) += error
    j += 1
    if j == n then
      i += 1
      j = i + 1

  i = 0
  while i < n do
    approxDiffTsError(i) /= n
    i += 1
  approxDists -> Some(approxDiffTsError)
}

def timed[T](f: => T): (T, Long) = {
  val t0 = System.nanoTime()
  val result = f
  val t1 = System.nanoTime()
  (result, Math.floorDiv(t1 - t0, 1_000_000))
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
  optional = List(
    "--resultFolder", "--dataFolder", "--distance", "--linkage", "--qualityMeasure", "--includeApproxDiff"
  ),
  parsed = Map(
    "resultFolder" -> s"$folder/experiments/ordering-strategy-analysis/",
    "dataFolder" -> s"$folder/data/datasets/",
    "distance" -> "msm",
    "linkage" -> "ward",
    "qualityMeasure" -> "approxAverageAri",
    "includeApproxDiff" -> "false"
  )
)
val validMeasures = Seq(
  "ari", "ariAt", "averageAri", "approxAverageAri", "labelChangesAt", "hierarchySimilarity",
  "weightedHierarchySimilarity"
)
val usage = "Usage: script <dataset> --resultFolder <resultFolder> --dataFolder <dataFolder> " +
  "--distance <sbd|msm|dtw|manhatten|euclidean|minkowsky> " +
  "--linkage <ward|single|complete|average|weighted> " +
  s"--qualityMeasure <${validMeasures.mkString("|")}> " +
  "--includeApproxDiff <true|false>"

///////////////////////////////////////////////////////////
val distanceName = options("distance").toLowerCase.strip
val linkageName = options("linkage").toLowerCase.strip
val distance = distanceName match {
  case "msm" => MSM(c = 0.5, window = 0.01, itakuraMaxSlope = Double.NaN)
  case "dtw" => DTW(window = 0.01, itakuraMaxSlope = Double.NaN)
  case "sbd" => SBD(standardize = false)
  case "manhatten" => Minkowsky(p = 1)
  case "euclidean" | "minkowsky" => Minkowsky(p = 2)
  case s => throw new IllegalArgumentException(s"Unknown distance distance: $s")
}
val linkage = Linkage(linkageName)
val dataset = options("dataset")
val qualityMeasure = options("qualityMeasure").strip
if !validMeasures.contains(qualityMeasure) then
  throw new IllegalArgumentException(s"Unknown quality measure: $qualityMeasure")

val inputDataFolder = {
  val f = options("dataFolder")
  if !f.endsWith("/") then f + "/" else f
}
val resultFolder = {
  val f = options("resultFolder")
  if !f.endsWith("/") then f + "-" + qualityMeasure + "/"
  else f.stripSuffix("/") + "-" + qualityMeasure + "/"
}
val includeApproxDiff = options("includeApproxDiff").toBoolean
val seed = 42
val orderingSamples = 1000
val maxHierarchySimilarities = 1000
///////////////////////////////////////////////////////////
new File(resultFolder).mkdirs()
val datasetTrainFile = {
  val f = new File(inputDataFolder + s"$dataset/${dataset}_TRAIN.ts")
  if f.exists() then Some(f) else None
}
val datasetTestFile = new File(inputDataFolder + s"$dataset/${dataset}_TEST.ts")
val rng = Random(seed)

println(s"Processing dataset: $dataset")
println("Configuration:")
println(s"  inputDataFolder = $inputDataFolder")
println(s"  resultFolder = $resultFolder")
println(s"  distance = $distance")
println(s"  linkage = $linkage")
println(s"  qualityMeasure = $qualityMeasure")
println(s"  includeApproxDiff = $includeApproxDiff")
println(s"  seed = $seed")
println(s"  orderingSamples = $orderingSamples")
println(s"  maxHierarchySimilarities = $maxHierarchySimilarities")
println()

// load time series
println("Loading time series ...")
val t0 = System.currentTimeMillis()
val trainTimeseries = datasetTrainFile.fold(Array.empty[LabeledTimeSeries])(f => TsParser.loadAllLabeledTimeSeries(f))
val timeseries = trainTimeseries ++ TsParser.loadAllLabeledTimeSeries(datasetTestFile, idOffset = trainTimeseries.length)
val n = timeseries.length
val m = n * (n - 1) / 2
val hierarchyCalcFactor = Math.floorDiv(m, Math.min(maxHierarchySimilarities, m))
println(s"... done in ${System.currentTimeMillis() - t0} ms")

println("Computing pairwise distances ...")
val t1 = System.currentTimeMillis()
val (approxDists, approxDiffTsErrorOpt) =
  if includeApproxDiff then computeApproxDistancesTwoMean(distance, timeseries, n)
  else computeApproxDistances(distance, timeseries, n)
val dists = PDist(distance.pairwise(timeseries.map(_.data)), n)
println(s"... done in ${System.currentTimeMillis() - t1} ms")

println(f"Mean distance approx: ${approxDists.mean}%.4f, std=${approxDists.std}%.4f")
println(f"Mean distance full: ${dists.mean}%.4f, std=${dists.std}%.4f")

// prepare ground truth
val approxHierarchy = computeHierarchy(approxDists, linkage)
val targetHierarchy = computeHierarchy(dists, linkage)
val targetLabels = CutTree(targetHierarchy, targetHierarchy.indices.toArray)
val targetClasses =
  if qualityMeasure == "ariAt" then CutTree(targetHierarchy, 20)
  else timeseries.map(_.label.toInt)

def calcQuality(h: Hierarchy): Double = qualityMeasure match {
  case "ari" => h.ari(targetClasses)
  case "ariAt" => h.ari(targetClasses)
  case "averageAri" => h.averageARI(targetLabels)
  case "approxAverageAri" => h.approxAverageARI(targetLabels)
  case "labelChangesAt" => h.labelChangesAt(targetHierarchy)
  case "hierarchySimilarity" => h.similarity(targetHierarchy)
  case "weightedHierarchySimilarity" => h.weightedSimilarity(targetHierarchy)
}

println("Using prelabels from approx distance hierarchy")
val nPreClasses = Math.sqrt(n).toInt * 3
val preLabels = CutTree(approxHierarchy, nPreClasses)

def executeStaticStrategy(strategy: Iterator[(Int, Int)]): (Array[(Int, Int)], Array[Double]) = {
  val order = Array.ofDim[(Int, Int)](m)
  val similarities = mutable.ArrayBuilder.make[Double]
  similarities.sizeHint(maxHierarchySimilarities + 2)
  val wDists = approxDists.mutableCopy
  similarities += calcQuality(approxHierarchy)

  var k = 0
  while strategy.hasNext do
    val (i, j) = strategy.next()
    wDists(i, j) = dists(i, j)
    val hierarchy = computeHierarchy(wDists, linkage)
    order(k) = (i, j)
    if k % hierarchyCalcFactor == 0 || k == m-1 then
      similarities += calcQuality(hierarchy)
    k += 1

  order -> similarities.result()
}

def executeDynamicStrategy(strategy: ApproxFullErrorWorkGenerator[Int]): (Array[(Int, Int)], Array[Double]) = {
  val order = Array.ofDim[(Int, Int)](m)
  val similarities = mutable.ArrayBuilder.make[Double]
  similarities.sizeHint(maxHierarchySimilarities + 2)
  val wDists = approxDists.mutableCopy
  similarities += calcQuality(approxHierarchy)

  var k = 0
  while strategy.hasNext do
    val (i, j) = strategy.next()
    val dist = dists(i, j)
    val error = approxDists(i, j) - dist
    wDists(i, j) = dist
    strategy.updateError(i, j, error)
    order(k) = (i, j)
    val hierarchy = computeHierarchy(wDists, linkage)
    if k % hierarchyCalcFactor == 0 || k == m-1 then
      similarities += calcQuality(hierarchy)
    k += 1

  order -> similarities.result()
}

def executePreClusterStrategy(strategyFactory: PDist => PreClusteringWorkGenerator[Int]): (Array[(Int, Int)], Array[Double]) = {
  val order = Array.ofDim[(Int, Int)](m)
  val similarities = mutable.ArrayBuilder.make[Double]
  similarities.sizeHint(maxHierarchySimilarities + 2)
  val wDists = approxDists.mutableCopy
  similarities += calcQuality(approxHierarchy)
  val strategy = strategyFactory(wDists)

  var k = 0
  while strategy.hasNext do
    val (i, j) = strategy.next()
    val dist = dists(i, j)
    wDists(i, j) = dist
    strategy.getPreClustersForMedoids(i, j).foreach { case (ids1, ids2, skip) =>
      for
        id1 <- ids1
        id2 <- ids2
        if (id1 != i || id2 != j) && id1 != skip && id2 != skip
      do
        val pair = if id1 < id2 then id1 -> id2 else id2 -> id1
        wDists(pair._1, pair._2) = dist
    }
    order(k) = (i, j)
    val hierarchy = computeHierarchy(wDists, linkage)
    if k % hierarchyCalcFactor == 0 || k == m-1 then
      similarities += calcQuality(hierarchy)
    k += 1

  order -> similarities.result()
}

// compute all orderings
val strategies: mutable.Map[String, WorkGenerator[Int]] = mutable.Map(
  "fcfs" -> FCFSWorkGenerator(0 until n),
  "shortestTs" -> ShortestTsWorkGenerator(
    timeseries.zipWithIndex.map((ts, i) => i -> ts.data.length).toMap
  ),
  "approxAscending" -> ApproxDistanceWorkGenerator(
    timeseries.indices.zipWithIndex.toMap, Set.empty, approxDists, Direction.Ascending
  ),
  "approxDescending" -> ApproxDistanceWorkGenerator(
    timeseries.indices.zipWithIndex.toMap, Set.empty, approxDists, Direction.Descending
  ),
  "gtLargestPairError" -> GtLargestPairErrorStrategy(approxDists, dists, timeseries.indices.toArray),
  "gtLargestTsError" -> GtLargestTsErrorStrategy(approxDists, dists, timeseries.indices.toArray),
  "approxFullError" -> ApproxFullErrorWorkGenerator(timeseries.indices.zipWithIndex.toMap),
)
approxDiffTsErrorOpt.foreach{ approxDiffTsError =>
  strategies += "approxDiffTsError" -> ApproxDiffTsErrorWorkGenerator(
    approxDiffTsError, timeseries.indices.zipWithIndex.toMap
  )
}
println(s"Executing all strategies for dataset $dataset with $n time series")
println(s"  n time series = $n")
println(s"  n pairs = ${n*(n-1)/2}")

// execute 1000 random orderings
val pb = ProgressBar.forTotal(orderingSamples + strategies.size + 2, format = ProgressBarFormat.FiraFont)
val fcfsOrder = FCFSWorkGenerator(0 until n).toArray
val randomQualities = Array.ofDim[Array[Double]](orderingSamples)
var i = 0
while i < orderingSamples do
  fcfsOrder.shuffleInPlace(rng)
  val (_, quality) = executeStaticStrategy(fcfsOrder.iterator)
  randomQualities(i) = quality
  pb.step()
  i += 1

// execute all defined strategies
val (namesIt, resultsIt) = strategies.map {
  case (name, s: ApproxFullErrorWorkGenerator[_]) =>
    val ((order, qualities), duration) = timed { executeDynamicStrategy(s) }
    pb.step()
    name -> (order, qualities, duration)
  case (name, s) =>
    val ((order, qualities), duration) = timed { executeStaticStrategy(s) }
    pb.step()
    name -> (order, qualities, duration)
}.concat(Seq(
// execute preclustering strategies
  {
    val ((order, qualities), duration) = timed {
      val preClusters = {
        val clusters = Array.ofDim[Array[Int]](nPreClasses)
        for i <- 0 until nPreClasses do
          clusters(i) = preLabels.zipWithIndex.filter(_._1 == i).map(_._2)
        clusters
      }
      executePreClusterStrategy(
        wdist => OrderedPreClusteringWorkGenerator[Int](timeseries.indices.zipWithIndex.toMap, preClusters, wdist)
      )
    }
    pb.step()
    "preClustering" -> (order, qualities, duration)
  }, {
    val ((order, qualities), duration) = timed {
      executePreClusterStrategy(
        wdist => RecursivePreClusteringStrategy(timeseries.indices.toArray, preLabels, wdist, linkage)
      )
    }
    pb.step()
    "recursivePreClustering" -> (order, qualities, duration)
  }
)).unzip
pb.finish()

val names = namesIt.toArray
val (orders, qualities, durations) = resultsIt.toArray.unzip3
val aucs = qualities.map(sim => sim.sum / sim.length)
for i <- names.indices do
  println(f"  ${names(i)} (${aucs(i)}%.2f) in ${durations(i)} ms")
println()

println(s"Computed qualities for all orderings, storing to CSVs ...")
val results = names.zipWithIndex.map((name, i) => name -> (i, aucs(i), durations(i), orders(i))).toMap
writeStrategiesToCsv(results, resultFolder + s"strategies-$n-$dataset.csv")
CSVWriter.write(resultFolder + s"traces-$n-$dataset.csv", qualities ++ randomQualities)
println("Done!")
