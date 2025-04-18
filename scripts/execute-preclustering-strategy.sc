//> using scala 3.3.3
//> using repository central
//> using repository ivy2local
//> using repository m2local
//> using repository https://repo.akka.io/maven
//> using dep de.hpi.fgis:progress-bar_3:0.1.0
//> using dep de.hpi.fgis:dendrotime_3:0.0.0+174-c28bcd6e+20241220-1345
//> using file Strategies.sc
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.{DTW, Distance, MSM, SBD}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, Linkage, computeHierarchy}
import de.hpi.fgis.dendrotime.clustering.metrics.AdjustedRandScore
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVWriter
import de.hpi.fgis.dendrotime.io.{CSVReader, CSVWriter, TsParser}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries
import de.hpi.fgis.dendrotime.structures.*
import de.hpi.fgis.dendrotime.structures.HierarchyWithBitset.given
import de.hpi.fgis.dendrotime.clustering.metrics.HierarchyMetricOps.given
import de.hpi.fgis.dendrotime.structures.strategies.*
import de.hpi.fgis.dendrotime.structures.strategies.ApproxDistanceWorkGenerator.Direction
import de.hpi.fgis.progressbar.{ProgressBar, ProgressBarFormat}

import java.io.{File, FileOutputStream, PrintWriter}
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Random, Using}

import Strategies.AdvancedPreClusteringStrategy

extension (order: Array[(Int, Int)])
  def toCsvRecord: String = order.map(t => s"(${t._1},${t._2})").mkString("\"", " ", "\"")

extension (hierarchy: Hierarchy) {
  def ind(otherHierarchy: Hierarchy): Double = 1 - hierarchy.approxAverageARI(otherHierarchy)
  def ind2(otherHierarchy: Hierarchy): Double = hierarchy.labelChangesAt(otherHierarchy)
}

def writeStrategiesToCsv(strategies: Map[String, (Int, Double, Long, Array[(Int, Int)])],
                         filename: String,
                         append: Boolean = false): Unit = {
  val data = strategies.map { case (name, (index, auc, duration, order)) =>
    s"$name,$index,$auc,$duration,${order.toCsvRecord}"
  }
  val header = "strategy,index,quality,time_ms,order"
  val content = Seq(header) ++ data
  Using.resource(new PrintWriter(new FileOutputStream(new File(filename), append), true, Charset.forName("UTF-8"))){ writer =>
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
    val d = distance(
      ts1.data.slice(ts1Center - snippetSize / 2, ts1Center + snippetSize / 2),
      ts2.data.slice(ts2Center - snippetSize / 2, ts2Center + snippetSize / 2)
    ) * scale
    // taint approx values by using a specific precision
//    approxDists(i, j) = (d * 1e6).toInt.toDouble / 1e6
    approxDists(i, j) = d
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
  optional = List("--resultFolder", "--dataFolder", "--qualityMeasure", "--metric", "--linkage", "--jet", "--strategy"),
  parsed = Map(
    "resultFolder" -> s"$folder/experiments/preclustering/",
    "dataFolder" -> s"$folder/data/datasets/",
    "qualityMeasure" -> "ari",
    "metric" -> "msm",
    "linkage" -> "ward",
    "jet" -> "false",
    "strategy" -> "all"
  )
)
val usage = "Usage: script <dataset> --resultFolder <resultFolder> --dataFolder <dataFolder> --qualityMeasure {hierarchy|ari|weighted|target_ari|averageARI}" +
            "--metric {sbd|msm|dtw} --linkage {ward|single|complete|average|weighted} --jet {true|false} --strategy {simple|recursive|advanced|all}"

///////////////////////////////////////////////////////////
val distanceName = options("metric").toLowerCase.strip
val linkageName = options("linkage").toLowerCase.strip
val distance = distanceName match {
  case "msm" => MSM(c = 0.5, window = Double.NaN, itakuraMaxSlope = Double.NaN)
  case "dtw" => DTW(window = 0.1, itakuraMaxSlope = Double.NaN)
  case "sbd" => SBD(standardize = false)
  case s => throw new IllegalArgumentException(s"Unknown distance metric: $s")
}
val linkage = Linkage(linkageName)
val dataset = options("dataset")
val qualityMeasure = options("qualityMeasure").toLowerCase.strip
val useJetPreClusters = options("jet").toBoolean
val strategyName = options("strategy").toLowerCase.strip
val strategyNames = if strategyName == "all" then Seq("simple", "advanced", "recursive")
                    else if strategyName == "simple" then Seq("simple", "simple-ind1", "simple-ind2")
                    else Seq(strategyName)
val inputDataFolder = {
  val f = options("dataFolder")
  if !f.endsWith("/") then f + "/" else f
}
val _suffix = s"${if useJetPreClusters then "-jet" else ""}-$qualityMeasure/"
val resultFolder = {
  val f = options("resultFolder")
  if !f.endsWith("/") then f + _suffix
  else f.stripSuffix("/") + _suffix
}
val maxHierarchySimilarities = 1000
///////////////////////////////////////////////////////////
new File(resultFolder).mkdirs()
val datasetTrainFile = {
  val f = new File(inputDataFolder + s"$dataset/${dataset}_TRAIN.ts")
  if f.exists() then Some(f) else None
}
val datasetTestFile = new File(inputDataFolder + s"$dataset/${dataset}_TEST.ts")
val prelabelsFile = new File(resultFolder.stripSuffix(_suffix) + s"/$dataset-prelabels.csv")

println(s"Processing dataset: $dataset")
println("Configuration:")
println(s"  inputDataFolder = $inputDataFolder")
println(s"  resultFolder = $resultFolder")
println(s"  bestOrderFile = $prelabelsFile")
println(s"  distance = $distance")
println(s"  linkage = $linkage")
println(s"  maxHierarchySimilarities = $maxHierarchySimilarities")
println(s"  qualityMeasure = $qualityMeasure")
println(s"  useJetPreClusters = $useJetPreClusters")
println()

// load time series
println("Loading time series ...")
val trainTimeseries = datasetTrainFile.fold(Array.empty[LabeledTimeSeries])(f => TsParser.loadAllLabeledTimeSeries(f))
val timeseries = trainTimeseries ++ TsParser.loadAllLabeledTimeSeries(datasetTestFile, idOffset = trainTimeseries.length)
val n = timeseries.length
val m = n * (n - 1) / 2
val hierarchyCalcFactor = Math.floorDiv(m, Math.min(maxHierarchySimilarities, m))

println("Computing pairwise distances ...")
val t0 = System.nanoTime()
val approxDists = computeApproxDistances(distance, timeseries, n)
val dists = PDist(distance.pairwise(timeseries.map(_.data)), n)
println(s"... done in ${(System.nanoTime() - t0) / 1_000_000} ms")

// save target distance matrices
var matrix = Array.ofDim[Double](n, n)
var i = 0
while i < n do
  var j = 0
  while j < n do
    matrix(i)(j) = approxDists(i, j)
    j += 1
  i += 1
CSVWriter.write(resultFolder + s"approx-distances-$distanceName-$dataset.csv", matrix)

matrix = Array.ofDim[Double](n, n)
i = 0
while i < n do
  var j = 0
  while j < n do
    matrix(i)(j) = dists(i, j)
    j += 1
  i += 1
CSVWriter.write(resultFolder + s"full-distances-$distanceName-$dataset.csv", matrix)

// prepare ground truth
val approxHierarchy = computeHierarchy(approxDists, linkage)
val targetHierarchy = computeHierarchy(dists, linkage)

val classes =
  if qualityMeasure == "target_ari" then CutTree(targetHierarchy, 20)
  else trainTimeseries.map(_.label.toInt)
val nClasses = classes.distinct.length

val (preClasses: Int, preLabels: Array[Int]) =
  if useJetPreClusters then
    // load TS order
    println("Loading prelabels ...")
    val preLabels = CSVReader.parse[Double](prelabelsFile).map(a => a(0).toInt)
    println(s"  prelabels: ${preLabels.mkString(", ")}")
    val requiredN = Math.sqrt(n).toInt * 3
    val gotN = preLabels.distinct.length
    require(requiredN == gotN, s"Expected 3 * sqrt(n)=$requiredN, but got $gotN prelabels")
    println("... done.")
    requiredN -> preLabels
  else
    println("Using prelabels from approx distance hierarchy")
    val preClasses = Math.sqrt(n).toInt * 3
    preClasses -> CutTree(approxHierarchy, preClasses)

// compute ordering
println(s"Executing strategy for dataset $dataset with $n time series")
println(s"  n time series = $n")
println(s"  n pairs = ${n*(n-1)/2}")

// execute strategy
if strategyNames == Seq("recursive") && qualityMeasure == "weighted" && (dataset == "BirdChicken" || dataset == "BeetleFly") then
  println(s"  DEBUG: Pair 0: writing approx hierarchy")
  HierarchyCSVWriter.write(resultFolder + s"hierarchy-$distanceName-$linkageName-$dataset-step0.csv", approxHierarchy)

val qualities = mutable.ArrayBuilder.make[Array[Double]]
val results =
  for (name, idx) <- strategyNames.zipWithIndex yield
    println(s"Executing strategy: $name")
    val t1 = System.nanoTime()
    val order = Array.ofDim[(Int, Int)](m)
    val similarities = mutable.ArrayBuilder.make[Double]
    similarities.sizeHint(maxHierarchySimilarities + 2)
    val wDists = approxDists.mutableCopy
    similarities += (
      if name.endsWith("ind1") then approxHierarchy.ind(approxHierarchy)
      else if name.endsWith("ind2") then approxHierarchy.ind2(approxHierarchy)
      else if qualityMeasure == "hierarchy" then approxHierarchy.similarity(targetHierarchy)
      else if qualityMeasure == "weighted" then approxHierarchy.weightedSimilarity(targetHierarchy)
      else if qualityMeasure == "averageari" then approxHierarchy.approxAverageARI(targetHierarchy)
      else approxHierarchy.ari(classes, nClasses)
    )
    val debugExactDists = mutable.BitSet.empty
    debugExactDists.sizeHint(m)
    val preClusters = {
      val clusters = Array.ofDim[Array[Int]](preClasses)
      for i <- 0 until preClasses do
        clusters(i) = preLabels.zipWithIndex.filter(_._1 == i).map(_._2)
      clusters
    }
    val strategy = name match {
      case "simple" => OrderedPreClusteringWorkGenerator[Int](timeseries.indices.zipWithIndex.toMap, preClusters, wDists)
      case s"simple-$_" => OrderedPreClusteringWorkGenerator[Int](timeseries.indices.zipWithIndex.toMap, preClusters, wDists)
      case "advanced" => AdvancedPreClusteringStrategy(timeseries.indices.toArray, preLabels, wDists)
      case "recursive" => RecursivePreClusteringStrategy(timeseries.indices.toArray, preLabels, wDists, linkage)
      case name => throw new IllegalArgumentException(s"Unknown strategy: $name")
    }

    var prevHierarchy: Hierarchy = approxHierarchy
    var k = 0
    while strategy.hasNext do
      val (i, j) = strategy.next()
      val d = dists(i, j)
      wDists(i, j) = d
      debugExactDists += PDist.index(i, j, n)
      strategy.getPreClustersForMedoids(i, j).foreach { case (ids1, ids2, skip) =>
        for
          id1 <- ids1
          id2 <- ids2
          if (id1 != i || id2 != j) && id1 != skip && id2 != skip
        do
          val pair = if id1 < id2 then id1 -> id2 else id2 -> id1
          if debugExactDists.contains(PDist.index(pair._1, pair._2, n)) then
            println(s"Pair $pair already set exactly!")
          else
            wDists(pair._1, pair._2) = d
      }
      require(!order.contains((i, j)), s"Pair ($i, $j) already processed!")

      order(k) = (i, j)
      val hierarchy = computeHierarchy(wDists, linkage)
      if k % hierarchyCalcFactor == 0 || k == m - 1 then
        similarities += (
          if name.endsWith("ind1") then hierarchy.ind(prevHierarchy)
          else if name.endsWith("ind2") then hierarchy.ind2(prevHierarchy)
          else if qualityMeasure == "hierarchy" then hierarchy.similarity(targetHierarchy)
          else if qualityMeasure == "weighted" then hierarchy.weightedSimilarity(targetHierarchy)
          else if qualityMeasure == "averageari" then hierarchy.approxAverageARI(targetHierarchy)
          else hierarchy.ari(classes, nClasses)
        )
        prevHierarchy = hierarchy
      if name == "recursive" && qualityMeasure == "weighted" && (dataset == "BirdChicken" || dataset == "BeetleFly") then
        println(s"  DEBUG: Pair ${k+1}: ($i, $j), writing hierarchy")
        HierarchyCSVWriter.write(resultFolder + s"hierarchy-$distanceName-$linkageName-$dataset-step${k+1}.csv", hierarchy)
      k += 1
    require(k == m, s"Expected to process $m pairs, but only processed $k")
    require((0 until n).forall(x => (x until n).forall(y => wDists(x, y) == dists(x, y))), "Not all distances set exactly!")
    val trace =
      if name.substring(0, name.length - 1).endsWith("ind") then
        val sim = similarities.result()
        val cumsum = Array.ofDim[Double](sim.length)
        var i = 0
        var sum = 0.0
        while i < sim.length do
          sum += sim(i)
          cumsum(i) = sum
          i += 1
        // scale to [0, 1]
        cumsum.map(x => x / sum)
      else
        similarities.result()
    qualities += trace
    val duration = System.nanoTime() - t1

    println(s"   order = ${order.toCsvRecord.substring(0, 100)} ...")
    val auc = trace.sum / trace.length
    println(f"  AUC = $auc%.4f")
    println(s"  duration = ${duration / 1_000_000} ms")
    println()

    if strategyNames.length == 1 || name == "advanced" then
      strategy.storeDebugMessages(new File(resultFolder + s"preCluster-debug-$distanceName-$linkageName-$n-$dataset.csv"))

    name match {
      case "simple" => "preClustering" -> (idx, auc, duration, order)
      case "simple-log" => "preClustering-log" -> (idx, auc, duration, order)
      case "simple-weighted" => "preClustering-weighted" -> (idx, auc, duration, order)
      case "simple-ind1" => "preClustering-ind-averageARI" -> (idx, auc, duration, order)
      case "simple-ind2" => "preClustering-ind-changes@k" -> (idx, auc, duration, order)
      case "advanced" => "advancedPreClustering" -> (idx, auc, duration, order)
      case "recursive" => "recursivePreClustering" -> (idx, auc, duration, order)
    }

println(s"Computed qualities for all orderings, storing to CSVs ...")
CSVWriter.write(resultFolder + s"traces-$distanceName-$linkageName-$n-$dataset.csv", qualities.result())
writeStrategiesToCsv(results.toMap, resultFolder + s"strategies-$distanceName-$linkageName-$n-$dataset.csv")
println("Done!")
