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
@Measurement(iterations = 5, time = 5)
@Fork(1, jvmArgs = Array("-Xms2G", "-Xmx2G"))
@Threads(4)
class MultiCutTree {

  @Param(Array("100", "500", "1000"))
  var n: Int = _

  var hierarchy: Hierarchy = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    val dists = PDist.apply(Array.fill[Double](n, n) {
      Math.random()
    }, n)
    hierarchy = computeHierarchy(dists, WardLinkage)
  }

  @Benchmark
  def oldCutTreeLoop(): Array[Array[Int]] = {
    val b = mutable.ArrayBuilder.make[Array[Int]]
    var i = 2
    while i < hierarchy.size do
      val labels = OldCutTree(hierarchy, i)
      b += labels
      i += 1
    b.result()
  }

  @Benchmark
  def multiCutTreeLoop(): Array[Array[Int]] = {
    val b = mutable.ArrayBuilder.make[Array[Int]]
    var i = 2
    while i < hierarchy.size do
      val labels = OldCutTree(hierarchy, i)
      b += labels
      i += 1
    b.result()
  }

  @Benchmark
  def multiCutTree(): Array[Array[Int]] = {
    val cuts = Array.tabulate(hierarchy.size - 2)(i => i + 2)
    CutTree(hierarchy, cuts)
  }

  object OldCutTree {
    def apply(hierarchy: Hierarchy, nClusters: Int): Array[Int] = {
      val nobs = hierarchy.n
      val colIdx = nobs - nClusters

      var lastGroup = Array.tabulate(nobs)(i => i)
      if colIdx == 0 then
        lastGroup

      else
        var group = lastGroup
        val clusters = Array.ofDim[Array[Int]](hierarchy.length)
        for i <- 0 until hierarchy.length do
          val idx = buildCluster(hierarchy(i), clusters, nobs)
          clusters(i) = idx

          val newGroup = Array.copyOf(lastGroup, nobs)
          val (minValue, maxValue) = minMaxValues(lastGroup, idx)
          for i <- idx do
            newGroup(i) = minValue
          for j <- 0 until nobs do
            if lastGroup(j) > maxValue then
              newGroup(j) -= 1

          if colIdx == i + 1 then
            group = newGroup
          lastGroup = newGroup
        group
    }

    @inline
    private def buildCluster(node: Hierarchy.Node, clusters: Array[Array[Int]], nobs: Int): Array[Int] = {
      val clusterBuilder = mutable.ArrayBuilder.make[Int]
      clusterBuilder.sizeHint(node.cardinality)
      if node.cId1 < nobs then
        clusterBuilder.addOne(node.cId1)
      else
        clusterBuilder.addAll(clusters(node.cId1 - nobs))
      if node.cId2 < nobs then
        clusterBuilder.addOne(node.cId2)
      else
        clusterBuilder.addAll(clusters(node.cId2 - nobs))
      clusterBuilder.result()
    }

    @inline
    private def minMaxValues(lastGroup: Array[Int], indices: Array[Int]): (Int, Int) = {
      var minValue = lastGroup(indices(0))
      var maxValue = lastGroup(indices(0))
      for i <- indices do
        val value = lastGroup(i)
        if value < minValue then
          minValue = value
        if value > maxValue then
          maxValue = value
      minValue -> maxValue
    }
  }
}

/*
 * Local results:
 * Benchmark                       (n)  Mode  Cnt     Score      Error  Units
 * MultiCutTree.multiCutTree       100  avgt    5     0.049 ±    0.010  ms/op
 * MultiCutTree.multiCutTree       500  avgt    5     2.363 ±    1.141  ms/op
 * MultiCutTree.multiCutTree      1000  avgt    5     8.610 ±    2.571  ms/op
 * MultiCutTree.multiCutTreeLoop   100  avgt    5     2.396 ±    1.364  ms/op
 * MultiCutTree.multiCutTreeLoop   500  avgt    5   296.695 ±   41.026  ms/op
 * MultiCutTree.multiCutTreeLoop  1000  avgt    5  2292.604 ± 1001.897  ms/op
 * MultiCutTree.oldCutTreeLoop     100  avgt    5     2.273 ±    0.895  ms/op
 * MultiCutTree.oldCutTreeLoop     500  avgt    5   335.251 ±  124.824  ms/op
 * MultiCutTree.oldCutTreeLoop    1000  avgt    5  2226.559 ± 1809.598  ms/op
 */