//> using scala 3.3.3
//> using repository central
//> using repository ivy2local
//> using repository m2local
//> using repository https://repo.akka.io/maven
//> using dep de.hpi.fgis:progress-bar_3:0.1.0
//> using dep de.hpi.fgis:dendrotime_3:0.0.0+126-a0ac6db9+20241121-1454
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.{DTW, Distance, MSM, SBD}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{Linkage, computeHierarchy}
import de.hpi.fgis.dendrotime.io.{CSVWriter, TsParser}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries
import de.hpi.fgis.dendrotime.structures.HierarchyWithClusters.given
import de.hpi.fgis.progressbar.{ProgressBar, ProgressBarFormat}

import java.io.{File, PrintWriter}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Random, Using}

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
val usage = "Usage: script <dataset> --dataFolder <dataFolder> --resultFolder <resultFolder> " +
  "--metric <sbd|msm|dtw> --linkage <ward|single|complete|average|weighted>"
val options: Map[String, String] = parseOptions(
  args = args.toList,
  required = List("dataset"),
  optional = List("--dataFolder", "--resultFolder", "--metric", "--linkage"),
  // Define defaults here:
  parsed = Map(
    "dataFolder" -> s"$folder/data/datasets/",
    "resultFolder" -> s"$folder/experiments/approx-strategy-analysis/",
    "metric" -> "msm",
    "linkage" -> "ward"
  )
)

///////////////////////////////////////////////////////////
val distanceName = options("metric").toLowerCase.strip()
val distance = distanceName match {
  case "msm" => MSM(c = 0.5, window = 0.05, itakuraMaxSlope = Double.NaN)
  case "dtw" => DTW(window = 0.05, itakuraMaxSlope = Double.NaN)
  case "sbd" => SBD(standardize = false)
  case s => throw new IllegalArgumentException(s"Unknown distance metric: $s")
}
val linkageName = options("linkage").toLowerCase.strip()
val linkage = Linkage.apply(linkageName)
val dataset = options("dataset")
val inputDataFolder = {
  val f = options("dataFolder")
  if !f.endsWith("/") then f + "/" else f
}
val resultFolder = {
  val f = options("resultFolder")
  if !f.endsWith("/") then f + "/" else f
}
val snippetSize = 20
///////////////////////////////////////////////////////////
new File(resultFolder).mkdirs()
val targetFilename = new File(
  resultFolder + s"/approx-strategies-$dataset-$distanceName-$linkageName-size$snippetSize.csv"
).getCanonicalFile
val datasetTrainFile = {
  val f = new File(inputDataFolder + s"$dataset/${dataset}_TRAIN.ts")
  if f.exists() then Some(f) else None
}
val datasetTestFile = new File(inputDataFolder + s"$dataset/${dataset}_TEST.ts")

println(s"Processing dataset: $dataset")
println("Configuration:")
println(s"  inputDataFolder = $inputDataFolder")
println(s"  resultFolder = $resultFolder")
println(s"  distance = $distance")
println(s"  linkage = $linkage")
println(s"  snippetSize = $snippetSize")
println()

// load time series
println("Loading time series and preparing ground truth ...")
val t0 = System.currentTimeMillis()
val trainTimeseries = datasetTrainFile.fold(Array.empty[LabeledTimeSeries])(f => TsParser.loadAllLabeledTimeSeries(f))
val timeseries = trainTimeseries ++ TsParser.loadAllLabeledTimeSeries(datasetTestFile, idOffset = trainTimeseries.length)
val n = timeseries.length
// prepare ground truth
val dists = PDist(distance.pairwise(timeseries.map(_.data)), n)
val targetHierarchy = computeHierarchy(dists, linkage)
val t1 = System.currentTimeMillis()
println(s"... done in ${t1 - t0} ms")
println()

type Strategy = (LabeledTimeSeries, LabeledTimeSeries) => Double

def beginStrategy(ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
  distance(ts1.data.slice(0, snippetSize), ts2.data.slice(0, snippetSize))
}
def endStrategy(ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
  val n1 = ts1.data.length
  val n2 = ts2.data.length
  distance(ts1.data.slice(n1 - snippetSize, n1), ts2.data.slice(n2 - snippetSize, n2))
}
def centerStrategy(ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
  val ts1Center = ts1.data.length / 2
  val ts2Center = ts2.data.length / 2
  distance(
    ts1.data.slice(ts1Center - snippetSize / 2, ts1Center + snippetSize / 2),
    ts2.data.slice(ts2Center - snippetSize / 2, ts2Center + snippetSize / 2))
}
def offsetBeginStrategy(relOffset: Double, size: Int = snippetSize)
                       (ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
  val o1 = (ts1.data.length * relOffset).toInt
  val o2 = (ts2.data.length * relOffset).toInt
  distance(ts1.data.slice(o1, o1 + size), ts2.data.slice(o2, o2 + size))
}
def offsetEndStrategy(relOffset: Double, size: Int = snippetSize)
                     (ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
  val n1 = ts1.data.length
  val n2 = ts2.data.length
  val o1 = (n * relOffset).toInt
  val o2 = (n * relOffset).toInt
  distance(ts1.data.slice(n1 - o1 - size, n1 - o1), ts2.data.slice(n2 - o2 - size, n2 - o2))
}
def twoMeanStrategy(relOffset: Double)(ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
  val ob = offsetBeginStrategy(relOffset, snippetSize / 2)(ts1, ts2)
  val oe = offsetEndStrategy(relOffset, snippetSize / 2)(ts1, ts2)
  (ob + oe) / 2
}
val strategies = Map(
  "begin" -> beginStrategy,
  "end" -> endStrategy,
  "center" -> centerStrategy,
  "offsetBegin10" -> offsetBeginStrategy(0.1),
  "offsetBegin20" -> offsetBeginStrategy(0.2),
  "offsetEnd10" -> offsetEndStrategy(0.1),
  "offsetEnd20" -> offsetEndStrategy(0.2),
  "twoMean10" -> twoMeanStrategy(0.1),
  "twoMean20" -> twoMeanStrategy(0.2)
)

def testStrategy(s: Strategy): Double = {
  val pdistB = PDist.empty(n).mutable
  var i = 0
  var j = 1
  while i < n -1 && j < n do
    pdistB(i, j) = s(timeseries(i), timeseries(j))
    j += 1
    if j == n then
      i += 1
      j = i + 1

  val approxHierarchy = computeHierarchy(pdistB, linkage)
  approxHierarchy.similarity(targetHierarchy)
}

println("Computing pairwise distances ...")
val pb = ProgressBar.forTotal(strategies.size, format = ProgressBarFormat.FiraFont)
val names = Array.ofDim[String](strategies.size)
val qualities = Array.ofDim[Double](strategies.size)
val runtimes = Array.ofDim[Long](strategies.size)

var i = 0
val strategyNames = strategies.keys.toSeq.sorted
for key <- strategyNames do
  names(i) = key
  val start = System.currentTimeMillis()
  qualities(i) = testStrategy(strategies(key))
  runtimes(i) = System.currentTimeMillis() - start
  pb.step()
  i += 1
pb.finish()

println("Results:")
for i <- 0 until strategies.size do
  println(f"  ${names(i)}%-15s: ${qualities(i)}%.4f in ${runtimes(i)}%5d ms")

println(s"Writing results to disk: $targetFilename")
val header = "approx-strategy,dataset,metric,linkage,snippet-size,quality,time_ms"
val data = (0 until strategies.size).map { i =>
  s"${names(i)},$dataset,$distanceName,$linkageName,$snippetSize,${qualities(i)},${runtimes(i)}"
}
val content = Seq(header) ++ data
Using.resource(new PrintWriter(targetFilename, "UTF-8")) { writer =>
  content.foreach(writer.println)
}
println("Done!")
