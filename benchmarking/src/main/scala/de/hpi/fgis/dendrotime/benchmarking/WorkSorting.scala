package de.hpi.fgis.dendrotime.benchmarking

import de.hpi.fgis.dendrotime.clustering.PDist
import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit
import scala.collection.mutable

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@Threads(1)
class WorkSorting {

  @Param(Array("100", "500", "1000"))
  var n: Int = _

  var mapping: Map[Long, Int] = _
  var dists: PDist = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    mapping = Array.tabulate(n)(i => i.toLong -> i).toMap
    dists = PDist.apply(Array.fill[Double](n, n) {
      Math.random()
    }, n)
  }

  @Benchmark
  def createQueue(): Array[(Long, Long)] = {
    val ids = mapping.keys.toArray
    val builder = mutable.ArrayBuilder.make[(Double, Long, Long)]
    builder.sizeHint(dists.n * (dists.n - 1) / 2)
    for i <- 0 until ids.length - 1 do
      for j <- i + 1 until ids.length do
        var pair = ids(i) -> ids(j)
        if pair._2 < pair._1 then
          pair = pair.swap
        val distance = dists(mapping(pair._1), mapping(pair._2))
        builder += ((distance, pair._1, pair._2))
    val work = builder.result()
    work.sortInPlaceBy(_._1)
    val queue = work.map(t => (t._2, t._3))
    queue
  }

  @Benchmark
  def arraySortByLookup(): Array[(Long, Long)] = {
    val work = Array.ofDim[(Long, Long)](dists.n * (dists.n - 1) / 2)
    var i = 0
    while i < dists.n - 1 do
      var j = i + 1
      while j < dists.n do
        val idLeft = i.toLong
        val idRight = j.toLong
        work(PDist.index(i, j, dists.n)) = (idLeft, idRight)
        j += 1
      i += 1

    work.sortInPlaceBy((id1, id2) => dists(mapping(id1), mapping(id2)))
    work
  }

  @Benchmark
  def arraySortByIndex(): Array[(Long, Long)] = {
    val reverseMapping = mapping.map(_.swap)

    val indices = Array.ofDim[(Int, Int)](dists.n * (dists.n - 1) / 2)
    var i = 0
    while i < dists.n - 1 do
      var j = i + 1
      while j < dists.n do
        indices(PDist.index(i, j, dists.n)) = (i, j)
        j += 1
      i += 1
    indices.sortInPlaceBy(dists.apply)

    val work = Array.ofDim[(Long, Long)](dists.n * (dists.n - 1) / 2)
    var k = 0
    while k < indices.length do
      val (i, j) = indices(k)
      work(k) = (reverseMapping(i), reverseMapping(j))
      k += 1
    work
  }

  @Benchmark
  def arraySortByInplace(): Array[(Long, Long)] = {
    val ids = mapping.keys.toArray
    val data = Array.ofDim[(Double, Long, Long)](dists.n * (dists.n - 1) / 2)
    var k = 0
    var i = 0
    while i < dists.n - 1 do
      var j = i + 1
      while j < dists.n do
        var pair = ids(i) -> ids(j)
        if pair._2 < pair._1 then
          pair = pair.swap
        val dist = dists(mapping(pair._1), mapping(pair._2))
        data(k) = (dist, pair._1, pair._2)
        j += 1
        k += 1
      i += 1
    data.sortInPlaceBy(_._1)

    data.map(t => (t._2, t._3))
  }
}
