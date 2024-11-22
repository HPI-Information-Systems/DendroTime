//> using scala 3.3.3
//> using repository central
//> using repository ivy2local
//> using repository m2local
//> using repository https://repo.akka.io/maven
//> using dep de.hpi.fgis:progress-bar_3:0.1.0
//> using dep de.hpi.fgis:dendrotime_3:0.0.0+126-a0ac6db9+20241121-1454
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.{MSM, SBD}
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
    "resultFolder" -> s"$folder/experiments/ordering-strategy-analysis/",
    "dataFolder" -> s"$folder/data/datasets/",
    "qualityMeasure" -> "ari"
  )
)
val usage = "Usage: script <dataset> --resultFolder <resultFolder> --dataFolder <dataFolder> --qualityMeasure <hierarchy|ari>"

///////////////////////////////////////////////////////////
val distance = MSM(window = 0.05)
val linkage = Linkage.WardLinkage
//val dataset = "PickupGestureWiimoteZ"
//val dataset = "Coffee"
//val dataset = "BeetleFly"
//val dataset = "HouseTwenty"
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
  if !f.endsWith("/") then f + "/" else f
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
val approxDists = PDist(distance.pairwise(timeseries.map(_.data.slice(0, 10))), n)
val dists = PDist(distance.pairwise(timeseries.map(_.data)), n)
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

def executeDynamicStrategy(strategy: ApproxFullErrorWorkGenerator[Int]): (Array[(Int, Int)], Array[Double]) = {
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
    val dist = dists(i, j)
    val error = approxDists(i, j) - dist
    wDists(i, j) = dist
    strategy.updateError(i, j, error)
    order(k) = (i, j)
    val hierarchy = computeHierarchy(wDists, linkage)
    if k % hierarchyCalcFactor == 0 || k == m-1 then
      similarities += (
        if qualityMeasure == "hierarchy" then hierarchy.similarity(targetHierarchy)
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
