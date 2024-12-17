//> using scala 3.3.3
//> using repository central
//> using repository ivy2local
//> using repository m2local
//> using repository https://repo.akka.io/maven
//> using dep de.hpi.fgis:progress-bar_3:0.1.0
//> using dep de.hpi.fgis:dendrotime_3:0.0.0+147-b5bbd4d6+20241126-1012
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.{DTW, Distance, MSM, SBD}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Linkage, computeHierarchy}
import de.hpi.fgis.dendrotime.clustering.metrics.AdjustedRandScore
import de.hpi.fgis.dendrotime.io.{CSVWriter, TsParser}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries
import de.hpi.fgis.dendrotime.structures.HierarchyWithBitset.given
import de.hpi.fgis.progressbar.{ProgressBar, ProgressBarFormat}

import java.io.{File, FileOutputStream, FileWriter, PrintWriter}
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Random, Using}

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

object Result {
  val csvHeader: String = "approx-strategy,dataset,metric,linkage,snippetSize,quality,time_ms"
}
case class Result(
                   strategy: String,
                   dataset: String,
                   metric: String,
                   linkage: String,
                   snippetSize: Int,
                   quality: Double,
                   time: Long) {
  def toCsv: String = s"$strategy,$dataset,$metric,$linkage,$snippetSize,$quality,$time"
}

type Strategy = (LabeledTimeSeries, LabeledTimeSeries) => Double

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
  "--metric <sbd|msm|dtw> --linkage <ward|single|complete|average|weighted> --qualityMeasure <hierarchy|ari>\n" +
  "       script <dataset> --dataFolder <dataFolder> --resultFolder <resultFolder> --all true"
val options: Map[String, String] = parseOptions(
  args = args.toList,
  required = List("dataset"),
  optional = List("--dataFolder", "--resultFolder", "--metric", "--linkage", "--all", "--qualityMeasure"),
  // Define defaults here:
  parsed = Map(
    "dataFolder" -> s"$folder/data/datasets/",
    "resultFolder" -> s"$folder/experiments/approx-strategy-analysis/",
    "metric" -> "msm",
    "linkage" -> "ward",
    "all" -> "false",
    "qualityMeasure" -> "ari"
  )
)

///////////////////////////////////////////////////////////
val useAll = options("all").toLowerCase.strip.toBoolean
val distanceNames = if useAll then Seq("msm", "dtw", "sbd") else Seq(options("metric").toLowerCase.strip)
val linkageNames =
  if useAll then Seq("single", "complete", "average", "ward", "weighted")
  else Seq(options("linkage").toLowerCase.strip())
val qualityMeasure = options("qualityMeasure").toLowerCase.strip()
val dataset = options("dataset")
val inputDataFolder = {
  val f = options("dataFolder")
  if !f.endsWith("/") then f + "/" else f
}
val resultFolder = {
  val f = options("resultFolder")
  if !f.endsWith("/") then f + "-" + qualityMeasure + "/"
  else f.stripSuffix("/") + "-" + qualityMeasure + "/"
}
val snippetSize = 20
///////////////////////////////////////////////////////////
new File(resultFolder).mkdirs()
val targetFilename = new File(
  resultFolder + s"/approx-strategies-$dataset-$snippetSize.csv"
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
println(s"  distances = ${distanceNames.mkString(", ")}")
println(s"  linkages = ${linkageNames.mkString(", ")}")
println(s"  snippetSize = $snippetSize")
println()

// load time series
println("Loading time series ...")
val t0 = System.currentTimeMillis()
val trainTimeseries = datasetTrainFile.fold(Array.empty[LabeledTimeSeries])(f => TsParser.loadAllLabeledTimeSeries(f))
val timeseries = trainTimeseries ++ TsParser.loadAllLabeledTimeSeries(datasetTestFile, idOffset = trainTimeseries.length)
val n = timeseries.length
val classes = timeseries.map(_.label.toInt)
val nClasses = classes.distinct.length
println(s"... done in ${System.currentTimeMillis() - t0} ms")

// prepare result file
if !targetFilename.exists() then
  Using.resource(new PrintWriter(targetFilename, "UTF-8")) { writer =>
    writer.println(Result.csvHeader)
  }

for linkageName <- linkageNames do
  val linkage = Linkage(linkageName)
  for distanceName <- distanceNames do
    println()
    println(s"Processing linkage=$linkageName and distance=$distanceName")
    val distance = distanceName match {
      case "msm" => MSM(c = 0.5, window = 0.05, itakuraMaxSlope = Double.NaN)
      case "dtw" => DTW(window = 0.05, itakuraMaxSlope = Double.NaN)
      case "sbd" => SBD(standardize = false)
      case s => throw new IllegalArgumentException(s"Unknown distance metric: $s")
    }
    // prepare ground truth
    println("  preparing ground truth ...")
    val t0 = System.currentTimeMillis()
    val dists = PDist(distance.pairwise(timeseries.map(_.data)), n)
    val targetHierarchy = computeHierarchy(dists, linkage)
    val t1 = System.currentTimeMillis()
    println(s"... done in ${t1 - t0} ms")
    println()

    def beginStrategy(ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
      val scale = Math.max(ts1.data.length, ts2.data.length)/snippetSize
      distance(ts1.data.slice(0, snippetSize), ts2.data.slice(0, snippetSize)) * scale
    }
    def endStrategy(ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
      val n1 = ts1.data.length
      val n2 = ts2.data.length
      val scale = Math.max(n1, n2)/snippetSize
      distance(ts1.data.slice(n1 - snippetSize, n1), ts2.data.slice(n2 - snippetSize, n2)) * scale
    }
    def centerStrategy(ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
      val scale = Math.max(ts1.data.length, ts2.data.length)/snippetSize
      val ts1Center = ts1.data.length / 2
      val ts2Center = ts2.data.length / 2
      distance(
        ts1.data.slice(ts1Center - snippetSize / 2, ts1Center + snippetSize / 2),
        ts2.data.slice(ts2Center - snippetSize / 2, ts2Center + snippetSize / 2)
      ) * scale
    }
    def offsetBeginStrategy(relOffset: Double, size: Int = snippetSize)
                           (ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
      val scale = Math.max(ts1.data.length, ts2.data.length)/snippetSize
      val o1 = (ts1.data.length * relOffset).toInt
      val o2 = (ts2.data.length * relOffset).toInt
      distance(ts1.data.slice(o1, o1 + size), ts2.data.slice(o2, o2 + size)) * scale
    }
    def offsetEndStrategy(relOffset: Double, size: Int = snippetSize)
                         (ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
      val n1 = ts1.data.length
      val n2 = ts2.data.length
      val o1 = (n1 * relOffset).toInt
      val o2 = (n2 * relOffset).toInt
      val scale = Math.max(n1, n2)/snippetSize
      distance(ts1.data.slice(n1 - o1 - size, n1 - o1), ts2.data.slice(n2 - o2 - size, n2 - o2)) * scale
    }
    def twoMeanStrategy(relOffset: Double)(ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Double = {
      val scale = Math.max(ts1.data.length, ts2.data.length)/snippetSize
      val ob = offsetBeginStrategy(relOffset, snippetSize / 2)(ts1, ts2)
      val oe = offsetEndStrategy(relOffset, snippetSize / 2)(ts1, ts2)
      (ob + oe) / 2 * scale
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

//      println(f"  mean-comparison: ${pdistB.mean}%.4f vs. target: ${dists.mean}%.4f")
      val approxHierarchy = computeHierarchy(pdistB, linkage)
      if qualityMeasure == "hierarchy" then
        approxHierarchy.similarity(targetHierarchy)
      else
        val clusters = CutTree(approxHierarchy, nClasses)
        AdjustedRandScore(classes, clusters)
    }

    println("  computing pairwise distances ...")
    val pb = ProgressBar.forTotal(strategies.size, format = ProgressBarFormat.FiraFont)
    val results = Array.ofDim[Result](strategies.size)

    var i = 0
    val strategyNames = strategies.keys.toSeq.sorted
    for key <- strategyNames do
      val start = System.currentTimeMillis()
      val quality = testStrategy(strategies(key))
      val runtime = System.currentTimeMillis() - start
      results(i) = Result(
        strategy = key,
        dataset = dataset,
        metric = distanceName,
        linkage = linkageName,
        snippetSize = snippetSize,
        quality = quality,
        time = runtime
      )
      pb.step()
      i += 1
    pb.finish()
    println("  ... done.")

    val best = results.maxBy(_.quality)
    println(f"  best results: ${best.strategy} with quality ${best.quality}%.4f in ${best.time} ms")

    println(s"  adding results to file: $targetFilename")
    Using.resource(new PrintWriter(new FileWriter(targetFilename, Charset.forName("UTF-8"), true))) { writer =>
      results.map(_.toCsv).foreach(writer.println)
    }
println("Done!")
