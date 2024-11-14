//> using target.scala "3"
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.{Distance, MSM, SBD}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{Hierarchy, Linkage, computeHierarchy}
import de.hpi.fgis.dendrotime.io.{CSVReader, CSVWriter, TsParser}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.{LabeledTimeSeries, TimeSeries}

import java.io.{File, FileInputStream, PrintWriter}
import scala.Conversion
import scala.collection.{BitSet, mutable}
import scala.language.implicitConversions
import scala.util.{Random, Using}

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

def loadTimeSeries(file: File, seed: Int, maxTimeseries: Option[Int] = None): Array[LabeledTimeSeries] = {
  val rng = Random(seed)
  val parser = TsParser(TsParser.TsParserSettings(parseMetadata = false))
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
  val allTs = builder.result()
  val indices: Seq[Int] = rng.shuffle(allTs.indices).take(maxTimeseries.getOrElse(allTs.length))
  indices.map(allTs).toArray
}

def computeDistances(ts: Array[LabeledTimeSeries], distance: Distance): (PDist, PDist, Array[Double]) = {
  val n = ts.length
  val approxDists = PDist.empty(n).mutable
  val dists = PDist.empty(n).mutable
  val variances = mutable.ArrayBuilder.ofDouble()
  variances.sizeHint(n*(n-1)/2)
  for i <- 0 until n do
    for j <- i + 1 until n do
      val start = distance(ts(i).data.take(10), ts(j).data.take(10))
      val end = distance(ts(i).data.takeRight(10), ts(j).data.takeRight(10))
      variances += Math.abs(start - end)
      approxDists(i, j) = (start + end)/2
      dists(i, j) = distance(ts(i).data, ts(j).data)
  (approxDists, dists, variances.result())
}

def shortestTsStrategy(lengths: Map[Int, Int]): Array[(Int, Int)] = {
  val idLengths = lengths.iterator.toArray
  idLengths.sortInPlaceBy(_._2)
  val ids = idLengths.map(_._1)
  val tuples = for {
    j <- 1 until ids.length
    i <- 0 until j
    idLeft = ids(i)
    idRight = ids(j)
    pair = if idLeft < idRight then (idLeft, idRight)
           else (idRight, idLeft)
  } yield pair
  tuples.toArray
}

def highestVarStrategy(vars: Array[Double], ids: Array[Int]): Array[(Int, Int)] = {
  val data = for {
    i <- 0 until ids.length - 1
    j <- i + 1 until ids.length
    idLeft = ids(i)
    idRight = ids(j)
    variance = vars(PDist.index(idLeft, idRight, ids.length))
  } yield (variance, idLeft, idRight)
  data
    .sortBy(_._1)
    .map(t => (t._2, t._3))
    .reverse
    .toArray
}

def approxDistanceStrategy(dists: PDist, ids: Array[Int], direction: String = "ascending"): Array[(Int, Int)] = {
  val data = for {
    i <- 0 until ids.length - 1
    j <- i + 1 until ids.length
    idLeft = ids(i)
    idRight = ids(j)
  } yield (dists(idLeft, idRight), idLeft, idRight)
  val queue = data
    .sortBy(_._1)
    .map(t => (t._2, t._3))
    .toArray
  if direction == "ascending" then
    queue
  else
    queue.reverse
}

def gtLargeErrorStrategy(aDists: PDist, fDists: PDist, ids: Array[Int]): Array[(Int, Int)] = {
  val data = for {
    i <- 0 until ids.length - 1
    j <- i + 1 until ids.length
    idLeft = ids(i)
    idRight = ids(j)
    approx = aDists(idLeft, idRight)
    full = fDists(idLeft, idRight)
    error = Math.abs(approx - full)
  } yield (error, idLeft, idRight)
  data
    .sortBy(_._1)
    .map(t => (t._2, t._3))
    .reverse
    .toArray
}

def writeStrategiesToCsv(strategies: Map[String, (Int, Array[(Int, Int)])], filename: String): Unit = {
  val data = strategies.map { case (name, (index, order)) =>
    val orderStr = order.map(t => s"(${t._1},${t._2})").mkString(" ")
    s"$name,$index,\"$orderStr\""
  }
  val header = "strategy,index,order"
  val content = Seq(header) ++ data
  Using.resource(new PrintWriter(new File(filename), "UTF-8")){ writer =>
    content.foreach(writer.println)
  }
}

val n = 5
val distance = MSM(window = 0.05)
val seed = 42
val linkage = Linkage.WardLinkage
val inputDataFolder = "Documents/projects/DendroTime/data/datasets/"
val resultFolder = "Documents/projects/DendroTime/experiments/ordering-strategy-analysis/"

// load time series
var timeseries = loadTimeSeries(new File(inputDataFolder + "Coffee/Coffee_TEST.ts"), seed, Some(n))
// compute approx and full distances
var (approxDists, dists, vars) = computeDistances(timeseries, distance)
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
val shortestTs = shortestTsStrategy(
  timeseries.zipWithIndex.map((ts, i) => i -> ts.data.length).toMap
)
val highestVar = highestVarStrategy(vars, timeseries.indices.toArray)
val approxAscending = approxDistanceStrategy(approxDists, timeseries.indices.toArray, "ascending")
val approxDescending = approxDistanceStrategy(approxDists, timeseries.indices.toArray, "descending")
val gtLargeError = gtLargeErrorStrategy(approxDists, dists, timeseries.indices.toArray)
println(s"Computing all orderings for $n time series")
println(s"  n time series = $n")
println(s"  n pairs = ${n*(n-1)/2}")
println(s"  n pair orderings = ${(n*(n-1)/2).factorial}")
println(s"  fcfs\t\t= ${fcfs.mkString(", ")}")
println(s"  shortestTs\t= ${shortestTs.mkString(", ")}")
println(s"  highestVar\t= ${highestVar.mkString(", ")}")
println(s"  approxAscending\t= ${approxAscending.mkString(", ")}")
println(s"  approxDescending\t= ${approxDescending.mkString(", ")}")
println(s"  gtLargeError\t= ${gtLargeError.mkString(", ")}")
println()

var t0 = System.nanoTime()
var allOrderings = fcfs.permutations.toArray
var t1 = System.nanoTime()
println(s"Materialized all orderings in ${(t1 - t0) / 1e9} seconds, storing to CSV ...")
if !File(resultFolder + s"orderings-$n.csv").isFile then
  CSVWriter.write(
    resultFolder + s"orderings-$n.csv",
    allOrderings.map(_.flatMap(t => Array(t._1, t._2)))
  )


writeStrategiesToCsv(Map(
  "fcfs" -> (allOrderings.indexWhere(_.sameElements(fcfs)), fcfs),
  "shortestTs" -> (allOrderings.indexWhere(_.sameElements(shortestTs)), shortestTs),
  "highestVar" -> (allOrderings.indexWhere(_.sameElements(highestVar)), highestVar),
  "approxAscending" -> (allOrderings.indexWhere(_.sameElements(approxAscending)), approxAscending),
  "approxDescending" -> (allOrderings.indexWhere(_.sameElements(approxDescending)), approxDescending),
  "gtLargeError" -> (allOrderings.indexWhere(_.sameElements(gtLargeError)), gtLargeError)
), resultFolder + s"strategies-$n-Coffee-$seed.csv")
timeseries = null
System.gc()

//println("Computing similarities for all orderings")
//val total = allOrderings.length
//val traces = mutable.ArrayBuilder.make[Array[Double]]
//t0 = System.nanoTime()
//for order <- allOrderings do
//  traces += executeOrdering(order)
//  if traces.length % 100_000 == 0 then
//    val progress = traces.length.toDouble / total
//    println(f"Progress: $progress%.2f")
//
//t1 = System.nanoTime()
//
//approxDists = null
//dists = null
//approxHierarchy = null
//targetHierarchy = null
//allOrderings = null
//System.gc()
//
//
//val tracesArray = traces.result()
//println(s"Computed similarities for all orderings in ${(t1 - t0) / 1e9} seconds, storing to CSV ...")
//CSVWriter.write(resultFolder + s"traces-$n-Coffee-$seed.csv", tracesArray)
//println("Done!")
