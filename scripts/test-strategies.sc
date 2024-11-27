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
import de.hpi.fgis.dendrotime.io.{CSVWriter, TsParser}
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

extension (hierarchy: Hierarchy) {
  def quality(classes: Array[Int], nClasses: Int): Double = {
    val clusters = CutTree(hierarchy, nClasses)
    AdjustedRandScore(classes, clusters)
  }
}

private def jaccardSimilarity[T](s1: scala.collection.Set[T], s2: scala.collection.Set[T]): Double = {
  val intersection = s1 & s2
  val union = s1 | s2
  intersection.size.toDouble / union.size
}

extension (hc: HierarchyWithClusters) {
  def weightedSimilarity(other: HierarchyWithClusters): Double = {
    val n = hc.hierarchy.size
    // compute pairwise similarities between clusters
    val dists = Array.ofDim[Double](n, n)
    val thisClusters = hc.clusters.toArray
    val thatClusters = other.clusters.toArray
    for i <- thisClusters.indices do
      for j <- thatClusters.indices do
        dists(i)(j) = jaccardSimilarity[Int](thisClusters(i), thatClusters(j))

    // find matches greedily (because Jaccard similarity is symmetric)
    val matches = Array.ofDim[Int](n)
    var similaritySum = 0.0
    val matched = mutable.Set.empty[Int]
    val ids = thatClusters.indices.toArray
    for i <- thisClusters.indices do
      val sortedIds = ids.sortBy(id => -dists(i)(id))
      var j = 0
      while matched.contains(sortedIds(j)) do
        j += 1
      val matchId = sortedIds(j)
      matches(i) = matchId
      similaritySum += dists(i)(matchId)
      matched += matchId

//    println(s"0 sim: ${dists(0).map("%.2f".format(_)).mkString(", ")}")
//    println(s"0 matched with ${matches(0)}")
//    println(s"1 sim: ${dists(0).map("%.2f".format(_)).mkString(", ")}")
//    println(s"1 matched with ${matches(1)}")
    similaritySum / n
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

class GtLargestPairErrorStrategy(aDists: PDist, fDists: PDist, ids: Array[Int]) extends WorkGenerator[Int] {
  val data = (
    for {
      i <- 0 until ids.length - 1
      j <- i + 1 until ids.length
      idLeft = ids(i)
      idRight = ids(j)
      approx = aDists(idLeft, idRight)
      full = fDists(idLeft, idRight)
      error = Math.abs(approx - full)
    } yield (error, idLeft, idRight)
    ).sortBy(_._1)
    .map(t => (t._2, t._3))
    .reverse
    .toArray
  var i = 0

  override def sizeIds: Int = aDists.n
  override def sizeTuples: Int = aDists.size
  override def index: Int = i
  override def hasNext: Boolean = i < data.length
  override def next(): (Int, Int) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"GtLargestPairErrorStrategy has no (more) work {i=$i, data.length=${data.length}}"
      )
    else
      val result = data(i)
      i += 1
      result
  }
}

class GtLargestTsErrorStrategy(aDists: PDist, fDists: PDist, ids: Array[Int])
  extends WorkGenerator[Int] with TsErrorMixin(aDists.n, aDists.length) {

  override protected val errors: scala.collection.IndexedSeq[Double] = createErrorArray()
  private val tsIds = ids.sortBy(id => -errors(id))
  private var i = 0

  override def sizeIds: Int = aDists.n
  override def sizeTuples: Int = aDists.size
  override def index: Int = i
  override def hasNext: Boolean = i < aDists.length
  override def next(): (Int, Int) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"GtLargestTsErrorStrategy has no (more) work {i=$i/$sizeTuples}"
      )

    val result = nextLargestErrorPair(tsIds)
    i += 1
    if result._2 < result._1 then
      result.swap
    else
      result
  }

  private def createErrorArray(): Array[Double] = {
    val n = ids.length
    val errors = Array.ofDim[Double](n)
    var i = 0
    var j = 1
    while i < n - 1 && j < n do
      val idLeft = ids(i)
      val idRight = ids(j)
      val approx = aDists(idLeft, idRight)
      val full = fDists(idLeft, idRight)
      errors(i) += Math.abs(approx - full)
      errors(j) += Math.abs(approx - full)
      j += 1
      if j == n then
        i += 1
        j = i + 1

    i = 0
    while i < n do
      errors(i) /= n
      i += 1
    errors
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
  optional = List("--resultFolder", "--dataFolder", "--qualityMeasure", "--metric", "--linkage"),
  parsed = Map(
    "resultFolder" -> s"$folder/experiments/ordering-strategy-analysis/",
    "dataFolder" -> s"$folder/data/datasets/",
    "metric" -> "msm",
    "linkage" -> "ward",
    "qualityMeasure" -> "ari"
  )
)
val usage = "Usage: script <dataset> --resultFolder <resultFolder> --dataFolder <dataFolder> --qualityMeasure <hierarchy|ari|weighted> " +
  "--metric <sbd|msm|dtw> --linkage <ward|single|complete|average|weighted>"

///////////////////////////////////////////////////////////
val distanceName = options("metric").toLowerCase.strip
val linkageName = options("linkage").toLowerCase.strip()
val distance = distanceName match {
  case "msm" => MSM(c = 0.5, window = 0.05, itakuraMaxSlope = Double.NaN)
  case "dtw" => DTW(window = 0.05, itakuraMaxSlope = Double.NaN)
  case "sbd" => SBD(standardize = false)
  case s => throw new IllegalArgumentException(s"Unknown distance metric: $s")
}
val linkage = Linkage(linkageName)
val dataset = options("dataset")
val qualityMeasure = options("qualityMeasure").toLowerCase.strip
//val inputDataFolder = "Documents/projects/DendroTime/data/datasets/"
//val resultFolder = "Documents/projects/DendroTime/experiments/ordering-strategy-analysis/"
val inputDataFolder = {
  val f = options("dataFolder")
  if !f.endsWith("/") then f + "/" else f
}
val resultFolder = {
  val f = options("resultFolder")
  if !f.endsWith("/") then f + "-" + qualityMeasure + "/"
  else f.stripSuffix("/") + "-" + qualityMeasure + "/"
}
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
println(s"  seed = $seed")
println(s"  orderingSamples = $orderingSamples")
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

println(f"Mean distance approx: ${approxDists.mean}%.4f, std=${approxDists.std}%.4f")
println(f"Mean distance full: ${dists.mean}%.4f, std=${dists.std}%.4f")

// prepare ground truth
val approxHierarchy = computeHierarchy(approxDists, linkage)
val targetHierarchy = computeHierarchy(dists, linkage)
val sim = targetHierarchy.weightedSimilarity(targetHierarchy)
println(s"Target 2 Target weighted similarity = $sim")
//System.exit(0)

def executeStaticStrategy(strategy: Iterator[(Int, Int)]): (Array[(Int, Int)], Array[Double]) = {
  val order = Array.ofDim[(Int, Int)](m)
  val similarities = mutable.ArrayBuilder.make[Double]
  similarities.sizeHint(maxHierarchySimilarities + 2)
  val wDists = approxDists.mutableCopy
  similarities += (
    if qualityMeasure == "hierarchy" then approxHierarchy.similarity(targetHierarchy)
    else if qualityMeasure == "weighted" then approxHierarchy.weightedSimilarity(targetHierarchy)
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
        else if qualityMeasure == "weighted" then hierarchy.weightedSimilarity(targetHierarchy)
        else hierarchy.quality(classes, nClasses)
      )
    k += 1

  order -> similarities.result()
}

def executeDynamicStrategy(strategy: ApproxFullErrorWorkGenerator[Int]): (Array[(Int, Int)], Array[Double]) = {
  val order = Array.ofDim[(Int, Int)](m)
  val similarities = mutable.ArrayBuilder.make[Double]
  similarities.sizeHint(maxHierarchySimilarities + 2)
  val wDists = approxDists.mutableCopy
  similarities += (
    if qualityMeasure == "hierarchy" then approxHierarchy.similarity(targetHierarchy)
    else if qualityMeasure == "weighted" then approxHierarchy.weightedSimilarity(targetHierarchy)
    else approxHierarchy.quality(classes, nClasses)
  )

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
      similarities += (
        if qualityMeasure == "hierarchy" then hierarchy.similarity(targetHierarchy)
        else if qualityMeasure == "weighted" then hierarchy.weightedSimilarity(targetHierarchy)
        else approxHierarchy.quality(classes, nClasses)
      )
    k += 1

  order -> similarities.result()
}

def timed[T](f: => T): (T, Long) = {
  val t0 = System.nanoTime()
  val result = f
  val t1 = System.nanoTime()
  (result, Math.floorDiv(t1 - t0, 1_000_000))
}

// compute all orderings
val strategies: Map[String, WorkGenerator[Int]] = Map(
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
println(s"Executing all strategies for dataset $dataset with $n time series")
println(s"  n time series = $n")
println(s"  n pairs = ${n*(n-1)/2}")

// execute 1000 random orderings
val pb = ProgressBar.forTotal(orderingSamples + strategies.size, format = ProgressBarFormat.FiraFont)
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
    val res = timed { executeDynamicStrategy(s) }
    pb.step()
    name -> (res._1._1, res._1._2, res._2)
  case (name, s) =>
    val res = timed { executeStaticStrategy(s) }
    pb.step()
    name -> (res._1._1, res._1._2, res._2)
}.unzip
pb.finish()

val names = namesIt.toArray
val (orders, qualities, durations) = resultsIt.toArray.unzip3
val aucs = qualities.map(sim => sim.sum / sim.length)
for i <- names.indices do
  println(s"  ${names(i)} (${aucs(i)%.2f})")
println()

println(s"Computed qualities for all orderings, storing to CSVs ...")
val results = names.zipWithIndex.map((name, i) => name -> (i, aucs(i), durations(i), orders(i))).toMap
writeStrategiesToCsv(results, resultFolder + s"strategies-$n-$dataset.csv")
CSVWriter.write(resultFolder + s"traces-$n-$dataset.csv", randomQualities ++ qualities)
println("Done!")
