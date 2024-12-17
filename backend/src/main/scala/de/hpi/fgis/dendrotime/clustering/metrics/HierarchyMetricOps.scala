package de.hpi.fgis.dendrotime.clustering.metrics

import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy}
import de.hpi.fgis.dendrotime.structures.HierarchyWithBitset

import scala.collection.mutable

object HierarchyMetricOps {
  def apply(hierarchy: Hierarchy): HierarchyMetricOps = new HierarchyMetricOps(hierarchy)

  given Conversion[Hierarchy, HierarchyMetricOps] = HierarchyMetricOps.apply(_)

}

class HierarchyMetricOps(hierarchy: Hierarchy) extends AnyVal {
  def ari(trueClasses: Array[Int], nClasses: Int): Double = {
    val clusters = CutTree(hierarchy, nClasses)
    AdjustedRandScore(trueClasses, clusters)
  }

  def averageARI(targetLabels: Array[Array[Int]]): Double = {
    val b = mutable.ArrayBuilder.make[Double]
    var i = 2
    while i < hierarchy.size do
      val labels = CutTree(hierarchy, i)
      val ari = AdjustedRandScore(targetLabels(i), labels)
      b += ari
      i += 1
    val aris = b.result()
    aris.sum / aris.length
  }

  def averageARI(targetHierarchy: Hierarchy): Double = {
    val targetLabels = targetHierarchy.indices.map(CutTree(targetHierarchy, _)).toArray
    averageARI(targetLabels)
  }

  def approxAverageARI(targetHierarchy: Hierarchy): Double = approxAverageARI(targetHierarchy, 1.3)

  def approxAverageARI(targetHierarchy: Hierarchy, factor: Double): Double = {
    val targetLabels = targetHierarchy.indices.map(CutTree(targetHierarchy, _)).toArray
    approxAverageARI(targetLabels, factor)
  }

  def approxAverageARI(targetLabels: Array[Array[Int]]): Double = approxAverageARI(targetLabels, 1.3)

  def approxAverageARI(targetLabels: Array[Array[Int]], factor: Double): Double = {
    val n = Math.min(hierarchy.size, targetLabels.length)
    if n >= 2 then
      val b = mutable.ArrayBuilder.make[Double]
      var i = 2
      while i < n do
        val labels = CutTree(hierarchy, i)
        val ari = AdjustedRandScore(targetLabels(i), labels)
        b += ari
        i = Math.ceil(i * factor).toInt
      val aris = b.result()
      aris.sum / aris.length
    else
      0.0
  }

  def weightedSimilarity(otherHierarchy: Hierarchy)(using conv: Conversion[Hierarchy, HierarchyWithBitset]): Double = {
    val self = conv(hierarchy)
    val other = conv(otherHierarchy)
    val n = self.hierarchy.size
    // compute pairwise similarities between clusters
    val dists = Array.ofDim[Double](n, n)
    val thisClusters = self.clusters
    val thatClusters = other.clusters
    var i = 0
    while i < n do
      var j = i
      while j < n do
        val d = JaccardSimilarity(thisClusters(i), thatClusters(j))
        dists(i)(j) = d
        if i != j then
          dists(j)(i) = d
        j += 1
      i += 1

    // find matches greedily (because Jaccard similarity is symmetric)
    var similaritySum = 0.0
    val matched = mutable.BitSet.empty
    matched.sizeHint(n - 1)
    i = 0
    while i < n do
      var maxId = 0
      var maxValue = 0.0
      var j = 0
      while j < n do
        if !matched.contains(j) && dists(i)(j) > maxValue then
          maxId = j
          maxValue = dists(i)(j)
        j += 1
      similaritySum += maxValue
      matched += maxId
      i += 1

    similaritySum / n
  }
}
