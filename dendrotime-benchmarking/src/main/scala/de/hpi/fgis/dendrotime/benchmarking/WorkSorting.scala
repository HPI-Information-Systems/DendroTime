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
@Fork(1, jvmArgs = Array("-Xms16G", "-Xmx16G"))
@Threads(4)
class WorkSorting {

  @Param(Array("100", "500", "1000", "5000"))
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

/*
 * Local results:
 * Benchmark                        (n)   Mode  Cnt     Score     Error   Units
 *|-------------------------------|-----|------|---|---------|----------|------|
 * WorkSorting.arraySortByIndex     500  thrpt   10     0.015 ±   0.003  ops/ms
 * WorkSorting.arraySortByIndex    1000  thrpt   10     0.002 ±   0.001  ops/ms
 * WorkSorting.arraySortByIndex    2000  thrpt   10    ≈ 10⁻³            ops/ms
 * WorkSorting.arraySortByInplace   500  thrpt   10     0.012 ±   0.001  ops/ms
 * WorkSorting.arraySortByInplace  1000  thrpt   10     0.002 ±   0.001  ops/ms
 * WorkSorting.arraySortByInplace  2000  thrpt   10    ≈ 10⁻³            ops/ms
 * WorkSorting.arraySortByLookup    500  thrpt   10     0.005 ±   0.001  ops/ms
 * WorkSorting.arraySortByLookup   1000  thrpt   10     0.001 ±   0.001  ops/ms
 * WorkSorting.arraySortByLookup   2000  thrpt   10    ≈ 10⁻⁴            ops/ms
 * WorkSorting.createQueue          500  thrpt   10     0.012 ±   0.001  ops/ms
 * WorkSorting.createQueue         1000  thrpt   10     0.002 ±   0.001  ops/ms
 * WorkSorting.createQueue         2000  thrpt   10    ≈ 10⁻³            ops/ms
 * WorkSorting.arraySortByIndex     100   avgt    5     1.316 ±   0.159   ms/op
 * WorkSorting.arraySortByIndex     500   avgt    5    86.425 ±  53.804   ms/op
 * WorkSorting.arraySortByIndex    1000   avgt    5   562.002 ± 108.829   ms/op
 * WorkSorting.arraySortByInplace   100   avgt    5     1.150 ±   0.200   ms/op
 * WorkSorting.arraySortByInplace   500   avgt    5    97.350 ±  25.708   ms/op
 * WorkSorting.arraySortByInplace  1000   avgt    5   661.552 ± 242.250   ms/op
 * WorkSorting.arraySortByLookup    100   avgt    5     3.816 ±   0.101   ms/op
 * WorkSorting.arraySortByLookup    500   avgt    5   246.060 ± 135.994   ms/op
 * WorkSorting.arraySortByLookup   1000   avgt    5  1202.289 ± 268.211   ms/op
 * WorkSorting.createQueue          100   avgt    5     1.106 ±   0.036   ms/op
 * WorkSorting.createQueue          500   avgt    5    91.598 ±   8.875   ms/op
 * WorkSorting.createQueue         1000   avgt    5   566.237 ±  51.298   ms/op
 *
 * Server results
 * Benchmark                        (n)  Mode  Cnt      Score      Error  Units
 *|-------------------------------|-----|------|---|---------|----------|------|
 * WorkSorting.arraySortByIndex     100  avgt   10      1.386 ±    0.071  ms/op
 * WorkSorting.arraySortByIndex     500  avgt   10     72.660 ±    4.780  ms/op
 * WorkSorting.arraySortByIndex    1000  avgt   10    482.809 ±   19.197  ms/op
 * WorkSorting.arraySortByIndex    5000  avgt   10  21276.819 ± 1117.401  ms/op
 * WorkSorting.arraySortByInplace   100  avgt   10      1.232 ±    0.008  ms/op
 * WorkSorting.arraySortByInplace   500  avgt   10     87.878 ±    5.509  ms/op
 * WorkSorting.arraySortByInplace  1000  avgt   10    505.748 ±   40.672  ms/op
 * WorkSorting.arraySortByInplace  5000  avgt   10  20334.662 ± 2397.224  ms/op
 * WorkSorting.arraySortByLookup    100  avgt   10      3.904 ±    0.052  ms/op
 * WorkSorting.arraySortByLookup    500  avgt   10    213.911 ±    2.647  ms/op
 * WorkSorting.arraySortByLookup   1000  avgt   10   1157.266 ±   36.386  ms/op
 * WorkSorting.arraySortByLookup   5000  avgt   10  53385.723 ±  687.872  ms/op
 * WorkSorting.createQueue          100  avgt   10      1.216 ±    0.003  ms/op
 * WorkSorting.createQueue          500  avgt   10     88.846 ±    3.679  ms/op
 * WorkSorting.createQueue         1000  avgt   10    509.795 ±   36.432  ms/op
 * WorkSorting.createQueue         5000  avgt   10  19808.761 ± 1092.367  ms/op
 * Total time: 5668 s (01:34:28), completed Nov 13, 2024, 12:11:29PM
 *
 * --> Current implementation is the fastest for larger input sizes.
 * --> Do not sort!
 */
