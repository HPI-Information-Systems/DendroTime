//> using target.scala "3"
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.{Distance, MSM, SBD}
import de.hpi.fgis.dendrotime.clustering.hierarchy.{Hierarchy, Linkage, computeHierarchy}
import de.hpi.fgis.dendrotime.io.{CSVReader, CSVWriter, TsParser}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.{LabeledTimeSeries, TimeSeries}
import de.hpi.fgis.dendrotime.structures.HierarchyWithClusters.given
import de.hpi.fgis.dendrotime.structures.*
import de.hpi.fgis.dendrotime.structures.strategies.ApproxDistanceWorkGenerator.Direction
import de.hpi.fgis.dendrotime.structures.strategies.{ApproxDistanceWorkGenerator, ApproxFullErrorWorkGenerator, FCFSWorkGenerator, ShortestTsWorkGenerator}

import java.io.{File, PrintWriter}
import scala.Conversion
import scala.collection.{BitSet, mutable}
import scala.language.implicitConversions
import scala.util.{Random, Using}

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
  val allTs = TsParser.loadAllLabeledTimeSeries(file)
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
val seed = 2
val linkage = Linkage.WardLinkage
//val dataset = "PickupGestureWiimoteZ"
val dataset = "Coffee"
val inputDataFolder = "Documents/projects/DendroTime/data/datasets/"
val resultFolder = "Documents/projects/DendroTime/experiments/ordering-strategy-analysis/"

// load time series
val timeseries = loadTimeSeries(new File(inputDataFolder + s"$dataset/${dataset}_TEST.ts"), seed, Some(n))
// compute approx and full distances
val (approxDists, dists, vars) = computeDistances(timeseries, distance)
// prepare ground truth
val approxHierarchy = computeHierarchy(approxDists, linkage)
val targetHierarchy = computeHierarchy(dists, linkage)

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

def executeDynamicStrategy(mapping: Map[Int, Int]): Array[(Int, Int)] = {
  val order = mutable.ArrayBuilder.make[(Int, Int)]
  val strategy = ApproxFullErrorWorkGenerator(mapping)

  while strategy.hasNext do
    val nextPair = strategy.next()

//    println(s"Processing pair $nextPair")
    val (i, j) = nextPair
    val dist = dists(i, j)
    val error = approxDists(i, j) - dist
    strategy.updateError(i, j, error)
    order += nextPair

  order.result()
}

// compute all orderings
val fcfs = FCFSWorkGenerator(0 until n).toArray
val mapping = timeseries.indices.zipWithIndex.toMap
val shortestTs = ShortestTsWorkGenerator(
  timeseries.zipWithIndex.map((ts, i) => i -> ts.data.length).toMap
).toArray
val approxAscending = ApproxDistanceWorkGenerator(
  mapping, Set.empty, approxDists, Direction.Ascending
).toArray
val approxDescending = ApproxDistanceWorkGenerator(
  mapping, Set.empty, approxDists, Direction.Descending
).toArray
val gtLargeError = gtLargeErrorStrategy(approxDists, dists, timeseries.indices.toArray)
val dynamicError = executeDynamicStrategy(mapping)
println(s"Computing all orderings for $n time series")
println(s"  n time series = $n")
println(s"  n pairs = ${n*(n-1)/2}")
println(s"  n pair orderings = ${(n*(n-1)/2).factorial}")
println(s"  fcfs\t\t= ${fcfs.mkString(", ")}")
println(s"  shortestTs\t= ${shortestTs.mkString(", ")}")
println(s"  approxAscending\t= ${approxAscending.mkString(", ")}")
println(s"  approxDescending\t= ${approxDescending.mkString(", ")}")
println(s"  gtLargeError\t= ${gtLargeError.mkString(", ")}")
println(s"  dynamicError\t= ${dynamicError.mkString(", ")}")
println()

//var t0 = System.nanoTime()
//val allOrderings = fcfs.permutations.toArray
//var t1 = System.nanoTime()
//println(s"Materialized all orderings in ${(t1 - t0) / 1e9} seconds, storing to CSV ...")
//if !File(resultFolder + s"orderings-$n.csv").isFile then
//  CSVWriter.write(
//    resultFolder + s"orderings-$n.csv",
//    allOrderings.map(_.flatMap(t => Array(t._1, t._2)))
//  )
//
//
//writeStrategiesToCsv(Map(
//  "fcfs" -> (allOrderings.indexWhere(_.sameElements(fcfs)), fcfs),
//  "shortestTs" -> (allOrderings.indexWhere(_.sameElements(shortestTs)), shortestTs),
//  "approxAscending" -> (allOrderings.indexWhere(_.sameElements(approxAscending)), approxAscending),
//  "approxDescending" -> (allOrderings.indexWhere(_.sameElements(approxDescending)), approxDescending),
//  "gtLargeError" -> (allOrderings.indexWhere(_.sameElements(gtLargeError)), gtLargeError),
//  "dynamicError" -> (allOrderings.indexWhere(_.sameElements(dynamicError)), dynamicError)
//), resultFolder + s"strategies-$n-$dataset-$seed.csv")

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
//val tracesArray = traces.result()
//println(s"Computed similarities for all orderings in ${(t1 - t0) / 1e9} seconds, storing to CSV ...")
//CSVWriter.write(resultFolder + s"traces-$n-$dataset-$seed.csv", tracesArray)
//println("Done!")
