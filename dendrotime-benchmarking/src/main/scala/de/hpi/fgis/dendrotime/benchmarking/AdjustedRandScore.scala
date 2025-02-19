package de.hpi.fgis.dendrotime.benchmarking

import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage.WardLinkage
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, computeHierarchy}
import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit
import scala.collection.mutable

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(1, jvmArgs = Array("-Xms2G", "-Xmx2G"))
@Threads(4)
class AdjustedRandScore {

  @Param(Array("500", "1000", "5000"))
  var n: Int = _

  var trueLabels: Array[Int] = _
  var predLabels: Array[Int] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    trueLabels = Array.fill(n)(util.Random.nextInt(10))
    predLabels = Array.fill(n)(util.Random.nextInt(10))
  }

  @Benchmark
  def currentARI(): Double = {
    require(
      trueLabels.length == predLabels.length,
      s"Both label arrays must have the same length (${trueLabels.length} != ${predLabels.length})."
    )

    val classes = trueLabels.distinct.sorted.zipWithIndex.toMap
    val clusters = predLabels.distinct.sorted.zipWithIndex.toMap
    val contingency = Array.ofDim[Long](classes.size, clusters.size)
    for i <- trueLabels.indices do
      val trueClass = classes(trueLabels(i))
      val predClass = clusters(predLabels(i))
      contingency(trueClass)(predClass) += 1

    // use BigInt to avoid overflow/underflow
    val n = BigInt(trueLabels.length)
    val sumSquares = contingency.flatten.foldLeft(BigInt(0))((sum, x) => sum + x * x)
    val nC = Array.ofDim[BigInt](classes.size)
    for i <- 0 until classes.size do
      nC(i) = contingency(i).sum
    val nK = Array.ofDim[BigInt](clusters.size)
    for j <- 0 until clusters.size do
      nK(j) = contingency.foldLeft(0L)((a, b) => a + b(j))

    // 1,1
    val tp = sumSquares - n
    // 0,1
    val fp = contingency.map(row => row.zip(nK).map(_ * _).sum).sum - sumSquares
    // 1,0
    val fn = contingency.zip(nC).map((row, nCj) => row.map(_ * nCj).sum).sum - sumSquares
    // 0,0
    val tn = n * n - fp - fn - sumSquares

    if fn == 0 && fp == 0 then
      1.0
    else
      (BigDecimal(tp * tn - fn * fp) / BigDecimal((tp + fn) * (fn + tn) + (tp + fp) * (fp + tn))).doubleValue * 2.0
  }

  @Benchmark
  def nonsugarARI(): Double = {
    val classes = trueLabels.distinct.sorted.zipWithIndex.toMap
    val clusters = predLabels.distinct.sorted.zipWithIndex.toMap
    val contingency = Array.ofDim[Long](classes.size, clusters.size)
    for i <- trueLabels.indices do
      val trueClass = classes(trueLabels(i))
      val predClass = clusters(predLabels(i))
      contingency(trueClass)(predClass) += 1

    // use BigInt to avoid overflow/underflow
    val n = BigInt(trueLabels.length)
    var sumSquares = BigInt(0)
    var i = 0
    while i < classes.size do
      val row = contingency(i)
      var j = 0
      while j < clusters.size do
        val e = row(j)
        sumSquares += e * e
        j += 1
      i += 1

    val nC = Array.ofDim[Long](classes.size)
    i = 0
    while i < classes.size do
      nC(i) = contingency(i).sum
      i += 1
    val nK = Array.ofDim[Long](clusters.size)
    i = 0
    while i < classes.size do
      val row = contingency(i)
      var j = 0
      while j < clusters.size do
        nK(j) += row(j)
        j += 1
      i += 1

    // 1,1
    val tp = sumSquares - n
    // 0,1
    var fp = BigInt(0)
    i = 0
    while i < classes.size do
      val row = contingency(i)
      var j = 0
      while j < clusters.size do
        fp += row(j) * nK(j)
        j += 1
      i += 1
    fp -= sumSquares
    // 1,0
    var fn = BigInt(0)
    i = 0
    while i < classes.size do
      val row = contingency(i)
      var j = 0
      while j < clusters.size do
        fn += row(j) * nC(i)
        j += 1
      i += 1
    fn -= sumSquares
    // 0,0
    val tn = n * n - fp - fn - sumSquares

    if fn == 0 && fp == 0 then
      1.0
    else
      (BigDecimal(tp * tn - fn * fp) / BigDecimal((tp + fn) * (fn + tn) + (tp + fp) * (fp + tn))).doubleValue * 2.0
  }
}

/*
 * Local results:
 * Benchmark                       (n)  Mode  Cnt  Score   Error  Units
 * AdjustedRandScore.currentARI    100  avgt    5  0.016 ± 0.001  ms/op
 * AdjustedRandScore.currentARI    500  avgt    5  0.049 ± 0.083  ms/op
 * AdjustedRandScore.currentARI   1000  avgt    5  0.047 ± 0.005  ms/op
 * AdjustedRandScore.currentARI   5000  avgt    5  0.175 ± 0.021  ms/op
 * AdjustedRandScore.nonsugarARI   100  avgt    5  0.009 ± 0.002  ms/op
 * AdjustedRandScore.nonsugarARI   500  avgt    5  0.024 ± 0.002  ms/op
 * AdjustedRandScore.nonsugarARI  1000  avgt    5  0.038 ± 0.012  ms/op
 * AdjustedRandScore.nonsugarARI  5000  avgt    5  0.161 ± 0.039  ms/op
 * ==> is not worth it
 */