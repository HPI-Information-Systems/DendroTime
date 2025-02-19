package de.hpi.fgis.dendrotime.benchmarking

import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.structures.WelfordsAlgorithm
import org.apache.commons.math3.special.Erf
import org.apache.commons.math3.util.FastMath
import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit
import scala.util.Random

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 10)
@Measurement(iterations = 5, time = 10)
@Fork(1, jvmArgs = Array("-Xms16G", "-Xmx16G"))
@Threads(2)
class ApproxDistanceOrder {

  @Param(Array("1000", "5000", "10000", "20000"))
  var n: Int = _

  var dists: PDist = _
  var nBins: Int = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    dists = PDist.unsafeWrapArray(Array.fill(n * (n - 1) / 2)(Random.nextDouble()))
    nBins = (Math.log(dists.length) / Math.log(2)).toInt
  }

  @Benchmark
  def sorting(): Array[Double] = {
    val distances = dists.distances
    val segments = if nBins % 2 == 1 then nBins + 1 else nBins

    // sort distances
    distances.sortInPlace()

    // extracts pivots
    val pivots = Array.ofDim[Double](segments + 1)
    val binSize = distances.length / segments
    pivots(0) = Double.MinValue
    var i = 1
    while i < segments do
      pivots(i) = distances(i * binSize)
      i += 1
    pivots(pivots.length - 1) = Double.MaxValue
    pivots
  }

  @Benchmark
  def quantiles(): Array[Double] = {
    val distances = dists.distances
    val segments = if nBins % 2 == 1 then nBins + 1 else nBins

    // estimate normal distribution parameters
    val mean = distances.sum /distances.length
    var m2sum = 0.0
    var i = 0
    while i < distances.length do
      val x = distances(i)
      m2sum += FastMath.pow(x - mean, 2)
      i += 1
    val std = FastMath.sqrt(m2sum / distances.length)

    // create pivots based on normal distribution
    extractPivots(mean, std, segments)
  }

  @Benchmark
  def quantilesWelford(): Array[Double] = {
    val distances = dists.distances
    val segments = if nBins % 2 == 1 then nBins + 1 else nBins

    // estimate normal distribution parameters
    val stats = WelfordsAlgorithm(distances)
    val mean = stats.mean
    val std = stats.stdDev

    // create pivots based on normal distribution
    extractPivots(mean, std, segments)
  }

  @inline
  private def extractPivots(mean: Double, std: Double, segments: Int): Array[Double] = {
    val pivots = Array.ofDim[Double](segments + 1)
    var i = -segments / 2
    var j = 0
    while i <= segments / 2 do
      val p = i.toDouble / segments
      val x = customProbit(p)
      pivots(j) = mean + x * std
      i += 1
      j += 1

    pivots
  }

  @inline
  private def customProbit(x: Double): Double = Erf.erfInv(2 * x) * FastMath.sqrt(2.0)
}


// Benchmark results (local)
/*
 * Benchmark                      (binFactor)    (n)  Mode  Cnt      Score       Error  Units
 * ApproxDistanceOrder.quantiles            1    100  avgt    5      0.496 ±     0.058  ms/op
 * ApproxDistanceOrder.quantiles            1   1000  avgt    5     76.156 ±     6.399  ms/op
 * ApproxDistanceOrder.quantiles            1   5000  avgt    5   2385.841 ±   191.425  ms/op
 * ApproxDistanceOrder.quantiles            3    100  avgt    5      1.329 ±     0.026  ms/op
 * ApproxDistanceOrder.quantiles            3   1000  avgt    5    198.092 ±     1.944  ms/op
 * ApproxDistanceOrder.quantiles            3   5000  avgt    5   6621.455 ±   433.406  ms/op
 * ApproxDistanceOrder.quantiles            5    100  avgt    5      2.144 ±     0.127  ms/op
 * ApproxDistanceOrder.quantiles            5   1000  avgt    5    326.047 ±     4.206  ms/op
 * ApproxDistanceOrder.quantiles            5   5000  avgt    5  12256.202 ± 13975.645  ms/op
 * ApproxDistanceOrder.quantiles            1  10000  avgt    5  10411.513 ±   656.191  ms/op
 * ApproxDistanceOrder.quantiles            1  20000  avgt    5  45750.672 ±  2751.715  ms/op
 * ApproxDistanceOrder.sorting              1    100  avgt    5      0.265 ±     0.021  ms/op
 * ApproxDistanceOrder.sorting              1   1000  avgt    5     32.789 ±     0.277  ms/op
 * ApproxDistanceOrder.sorting              1   5000  avgt    5   1216.958 ±   261.928  ms/op
 * ApproxDistanceOrder.sorting              3    100  avgt    5      0.683 ±     0.079  ms/op
 * ApproxDistanceOrder.sorting              3   1000  avgt    5     92.541 ±     7.766  ms/op
 * ApproxDistanceOrder.sorting              3   5000  avgt    5   3038.637 ±   330.617  ms/op
 * ApproxDistanceOrder.sorting              5    100  avgt    5      1.109 ±     0.191  ms/op
 * ApproxDistanceOrder.sorting              5   1000  avgt    5    172.059 ±     6.357  ms/op
 * ApproxDistanceOrder.sorting              5   5000  avgt    5   4722.271 ±   784.452  ms/op
 * ApproxDistanceOrder.sorting              1  10000  avgt    5   7197.069 ±  3042.298  ms/op
 * ApproxDistanceOrder.sorting              1  20000  avgt    5  39574.433 ± 18448.401  ms/op
 *
 * without generator iterations:
 * ApproxDistanceOrder.quantiles            1  20000  avgt    5   1173.399 ±    22.724  ms/op
 * ApproxDistanceOrder.sorting              1  20000  avgt    5  17515.890 ± 14411.716  ms/op
 *
 * just metadata creation, but use Welford's algorithm or compute mean/std in the traditional way:
 * Benchmark                               (n)  Mode  Cnt      Score     Error  Units
 * ApproxDistanceOrder.quantiles          1000  avgt    5     17.974 ±   1.515  ms/op
 * ApproxDistanceOrder.quantilesWelford   1000  avgt    5      2.903 ±   0.056  ms/op
 * ApproxDistanceOrder.sorting            1000  avgt    5      1.913 ±   0.212  ms/op
 * ApproxDistanceOrder.quantiles          5000  avgt    5    448.591 ±  12.838  ms/op
 * ApproxDistanceOrder.quantilesWelford   5000  avgt    5     72.912 ±   2.404  ms/op
 * ApproxDistanceOrder.sorting            5000  avgt    5     59.992 ±   9.621  ms/op
 * ApproxDistanceOrder.quantiles         10000  avgt    5   1787.126 ±  39.758  ms/op
 * ApproxDistanceOrder.quantilesWelford  10000  avgt    5    290.821 ±   4.806  ms/op
 * ApproxDistanceOrder.sorting           10000  avgt    5   1840.890 ± 554.316  ms/op
 * ApproxDistanceOrder.quantiles         20000  avgt    5   7125.679 ± 225.560  ms/op
 * ApproxDistanceOrder.quantilesWelford  20000  avgt    5   1174.361 ±  30.939  ms/op
 * ApproxDistanceOrder.sorting           20000  avgt    5  15613.189 ± 915.796  ms/op
*/
