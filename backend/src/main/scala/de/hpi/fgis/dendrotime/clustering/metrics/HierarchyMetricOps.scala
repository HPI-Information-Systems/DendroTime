package de.hpi.fgis.dendrotime.clustering.metrics

import de.hpi.fgis.bloomfilter.BloomFilter
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy}
import de.hpi.fgis.dendrotime.structures.{HierarchyWithBF, HierarchyWithBitset}

import scala.collection.{BitSet, mutable}
import scala.reflect.ClassTag
import scala.util.Using

object HierarchyMetricOps {
  def apply(hierarchy: Hierarchy): HierarchyMetricOps = new HierarchyMetricOpsWrapper(hierarchy)

  given Conversion[Hierarchy, HierarchyMetricOps] = HierarchyMetricOps.apply(_)

  private final class HierarchyMetricOpsWrapper(hierarchy: Hierarchy) extends HierarchyMetricOps(hierarchy)
}

trait HierarchyMetricOps(hierarchy: Hierarchy) {

  /**
   * Computes the Adjusted Mutual Information score (AMI) compared to target classes.
   *
   * First cuts the hierarchy to achieve the same number of clusters as in the target classes, then
   * computes the AMI between the predicted clusters and the given target classes.
   *
   * @param trueClasses the target classes as integer labels
   * @return the Adjusted Mutual Information score (AMI) between the predicted clusters and the target classes
   */
  def ami(trueClasses: Array[Int]): Double = ami(trueClasses, trueClasses.distinct.length)

  /**
   * Computes the Adjusted Mutual Information score (AMI) compared to target classes.
   *
   * First cuts the hierarchy at the given number of clusters, then computes the AMI between the predicted clusters
   * and the given target classes.
   *
   * @param trueClasses the target classes as integer labels
   * @param nClasses the number of clusters to cut the hierarchy at, must correspond to the number of distinct
   *                 labels in `trueClasses`
   * @return the Adjusted Mutual Information score (AMI) between the predicted clusters and the target classes
   */
  def ami(trueClasses: Array[Int], nClasses: Int): Double = {
    val clusters = CutTree(hierarchy, nClasses)
    SupervisedClustering.ami(trueClasses, clusters)
  }

  /**
   * Computes the Adjusted Rand Index (ARI) compared to target classes.
   *
   * First cuts the hierarchy to achieve the same number of clusters as in the target classes, then
   * computes the ARI between the predicted clusters and the given target classes.
   *
   * @param trueClasses the target classes as integer labels
   * @return the Adjusted Rand Index (ARI) between the predicted clusters and the target classes
   */
  def ari(trueClasses: Array[Int]): Double = ari(trueClasses, trueClasses.distinct.length)

  /**
   * Computes the Adjusted Rand Index (ARI) for a given number of clusters/classes.
   *
   * First cuts the hierarchy at the given number of clusters, then computes the ARI between the predicted clusters
   * and the given target classes.
   *
   * @param trueClasses the target classes as integer labels
   * @param nClasses the number of clusters to cut the hierarchy at, must correspond to the number of distinct
   *                 labels in `trueClasses`
   * @return the Adjusted Rand Index (ARI) between the predicted clusters and the target classes
   */
  def ari(trueClasses: Array[Int], nClasses: Int): Double = {
    val clusters = CutTree(hierarchy, nClasses)
    SupervisedClustering.ari(trueClasses, clusters)
  }

  /**
   * Computes the average ARI for all possible numbers of clusters between this hierarchy and a target hierarchy.
   *
   * @param targetLabels the target labels for each level of the hierarchy, must include the labels for the root
   *                     level (0) until the maximum level (n)
   * @return the average ARI
   */
  def averageARI(targetLabels: Array[Array[Int]]): Double = {
    val n_clusters = Array.tabulate(hierarchy.size-2)(_ + 2)
    val labels = CutTree(hierarchy, n_clusters)
    var aris = 0.0
    var i = 0
    while i < labels.length do
      aris += SupervisedClustering.ari(targetLabels(i+2), labels(i))
      i += 1
    aris / labels.length
  }

  /**
   * Computes the average ARI for all possible numbers of clusters between this hierarchy and a target hierarchy.
   *
   * @param targetHierarchy the target hierarchy
   * @return the average ARI
   */
  def averageARI(targetHierarchy: Hierarchy): Double = {
    val targetLabels = CutTree(targetHierarchy, targetHierarchy.indices.toArray)
    averageARI(targetLabels)
  }

  /**
   * Approximates the average ARI between this hierarchy and a target hierarchy by computing the ARI for a subset of
   * cluster numbers. The subset is determined by the `factor` parameter. The ARI is computed the cluster numbers
   * starting from 2 and increasing by a factor of `factor` until the maximum number of clusters is reached.
   * A higher `factor` will result in fewer ARI computations and thus faster but less precise computation. A factor
   * of 1.0 will compute the ARI for all possible numbers of clusters.
   *
   * @param targetHierarchy the target hierarchy
   * @return approximation of the average ARI
   */
  def approxAverageARI(targetHierarchy: Hierarchy): Double = approxAverageARI(targetHierarchy, 1.3)

  /**
   * Approximates the average ARI between this hierarchy and a target hierarchy by computing the ARI for a subset of
   * cluster numbers. The subset is determined by the `factor` parameter. The ARI is computed the cluster numbers
   * starting from 2 and increasing by a factor of `factor` until the maximum number of clusters is reached.
   * A higher `factor` will result in fewer ARI computations and thus faster but less precise computation. A factor
   * of 1.0 will compute the ARI for all possible numbers of clusters.
   *
   * @param targetHierarchy the target hierarchy
   * @param factor the factor by which the number of clusters is increased in each iteration
   * @return approximation of the average ARI
   */
  def approxAverageARI(targetHierarchy: Hierarchy, factor: Double): Double = {
    val targetLabels = CutTree(targetHierarchy, targetHierarchy.indices.toArray)
    approxAverageARI(targetLabels, factor)
  }

  /**
   * Approximates the average ARI between this hierarchy and a target hierarchy by computing the ARI for a subset of
   * cluster numbers. The subset is determined by the `factor` parameter. The ARI is computed the cluster numbers
   * starting from 2 and increasing by a factor of `factor` until the maximum number of clusters is reached.
   * A higher `factor` will result in fewer ARI computations and thus faster but less precise computation. A factor
   * of 1.0 will compute the ARI for all possible numbers of clusters.
   *
   * @param targetLabels the target labels for each level of the hierarchy, must include the labels for the root
   * @return approximation of the average ARI
   */
  def approxAverageARI(targetLabels: Array[Array[Int]]): Double = approxAverageARI(targetLabels, 1.3)

  /**
   * Approximates the average ARI between this hierarchy and a target hierarchy by computing the ARI for a subset of
   * cluster numbers. The subset is determined by the `factor` parameter. The ARI is computed the cluster numbers
   * starting from 2 and increasing by a factor of `factor` until the maximum number of clusters is reached.
   * A higher `factor` will result in fewer ARI computations and thus faster but less precise computation. A factor
   * of 1.0 will compute the ARI for all possible numbers of clusters.
   *
   * @param targetLabels the target labels for each level of the hierarchy, must include the labels for the root
   * @param factor the factor by which the number of clusters is increased in each iteration
   * @return approximation of the average ARI
   */
  def approxAverageARI(targetLabels: Array[Array[Int]], factor: Double): Double = {
    val n = Math.min(hierarchy.size, targetLabels.length)
    if n >= 2 then
      val b = mutable.ArrayBuilder.make[Int]
      b.sizeHint(n)
      var i = 2
      while i < n do
        b += i
        i = Math.ceil(i * factor).toInt
      val nClusters = b.result()

      val labels = CutTree(hierarchy, nClusters)
      var aris = 0.0
      i = 0
      while i < labels.length do
        aris += SupervisedClustering.ari(targetLabels(nClusters(i)), labels(i))
        i += 1
      aris / labels.length
    else
      0.0
  }

  /**
   * Cuts both hierarchies at a fixed height (to get k clusters) and compares the labels.
   * The result is the fraction of labels that changed.
   *
   * @param otherHierarchy the other hierarchy to compare to
   * @param k the desired number of clusters, defaults to n_instances / 2
   * @return the fraction of labels that changed
   */
  def labelChangesAt(otherHierarchy: Hierarchy, k: Int = hierarchy.n / 2): Double = {
    if k >= 2 then
      val labels = CutTree(hierarchy, k)
      val otherLabels = CutTree(otherHierarchy, k)
      val changes = labels.zip(otherLabels).map((x, y) => if x != y then 1 else 0)
      changes.sum.toDouble / changes.length
    else
      0.0
  }

  /**
   * Computes the Jaccard similarity between this hierarchy and another hierarchy based on representing each
   * cluster by its contained time series. The similarity is computed as the Jaccard similarity between the
   * sets of cluster representations that have at least 3 and at most n-1 elements.
   *
   * @param otherHierarchy the other hierarchy with clusters represented as BitSets
   * @param conv           the conversion from Hierarchy to HierarchyWithBitset
   * @return the similarity
   */
  def similarity(otherHierarchy: HierarchyWithBitset)(using conv: Conversion[Hierarchy, HierarchyWithBitset]): Double =
    similarity(otherHierarchy, 3, 1)

  /**
   * Computes the Jaccard similarity between this hierarchy and another hierarchy based on representing each
   * cluster by its contained time series. The similarity is computed as the Jaccard similarity between the
   * sets of cluster representations.
   *
   * @param otherHierarchy the other hierarchy with clusters represented as BitSets
   * @param cardLowerBound the lower bound for the cardinality of clusters to consider
   *                       (inclusive, default: 3)
   * @param cardUpperBound the upper bound for the cardinality of clusters to consider
   *                       (inclusive, default: 1 = at most n-1 elements)
   * @param conv           the conversion from Hierarchy to HierarchyWithBitset
   * @return the similarity
   */
  def similarity(otherHierarchy: HierarchyWithBitset, cardLowerBound: Int, cardUpperBound: Int)(using conv: Conversion[Hierarchy, HierarchyWithBitset]): Double =
    computeBoundedJaccardSimilarity(hierarchy.clusters, otherHierarchy.clusters, cardLowerBound, cardUpperBound)

  /**
   * Computes the Jaccard similarity between this hierarchy and another hierarchy based on representing each
   * cluster by its contained time series. The similarity is computed as the Jaccard similarity between the
   * sets of cluster representations.
   *
   * @param other the other hierarchy with clusters represented as BloomFilters
   * @param cardLowerBound the lower bound for the cardinality of clusters to consider
   *                       (inclusive, default: 3)
   * @param cardUpperBound the upper bound for the cardinality of clusters to consider
   *                       (inclusive, default: 1 = at most n-1 elements)
   * @param conv  the conversion from Hierarchy to HierarchyWithBF
   * @return the similarity
   */
  def similarity(other: HierarchyWithBF, cardLowerBound: Int = 3, cardUpperBound: Int = 1)(using conv: Conversion[Hierarchy, HierarchyWithBF]): Double =
    // only convert once to properly clean up resources
    Using.resource(conv(hierarchy)) { h =>
      computeBoundedJaccardSimilarity(h.clusters, other.clusters, cardUpperBound, cardLowerBound)
    }

  protected def computeBoundedJaccardSimilarity[T <: BitSet | BloomFilter[Int] : ClassTag](clusters1: Array[T], clusters2: Array[T], cardLowerBound: Int, cardUpperBound: Int): Double = {
    val n = clusters1.length
    val self = mutable.HashSet.empty[T]
    val other = mutable.HashSet.empty[T]

    var i = 0
    while i < n do
      val card = hierarchy.cardinality(i)
      if card >= cardLowerBound && card <= n - cardUpperBound then
        self.add(clusters1(i))
        other.add(clusters2(i))
      i += 1
    JaccardSimilarity(self, other)
  }

  /**
   * Computes the weighted similarity between this hierarchy and another hierarchy.
   *
   * The weighted similarity is the average Jaccard similarity between all clusters of the two hierarchies. The
   * cluster matches are chosen greedily to maximize the similarity.
   *
   * @param other the other hierarchy with clusters represented as BitSets
   * @param conv  the conversion from Hierarchy to HierarchyWithBitset
   * @return the weighted similarity
   */
  def weightedSimilarity(other: HierarchyWithBitset)(using conv: Conversion[Hierarchy, HierarchyWithBitset]): Double = {
    val n = hierarchy.size
    // compute pairwise similarities between clusters
    val sims = pairwiseClusterSimilarities(hierarchy.clusters, other.clusters)
    // find matches greedily (because Jaccard similarity is symmetric)
    val similaritySum = sumGreedyMatchedDists(sims)
    similaritySum / n
  }

  /**
   * Computes the weighted similarity between this hierarchy and another hierarchy.
   *
   * The weighted similarity is the average Jaccard similarity between all clusters of the two hierarchies. The
   * cluster matches are chosen greedily to maximize the similarity.
   *
   * @note This method creates and automatically removes the bloom filters of the current hierarchy. The caller
   *       is responsible for closing the resources (bloom filters) of the other hierarchy.
   * @param other the other hierarchy with clusters represented as BloomFilters
   * @param conv  the conversion from Hierarchy to HierarchyWithBF
   * @return the weighted similarity
   */
  def weightedSimilarity(other: HierarchyWithBF)(using conv: Conversion[Hierarchy, HierarchyWithBF]): Double = {
    // only convert once to properly clean up resources
    Using.resource(conv(hierarchy)) { h =>
      val n = h.length
      // compute pairwise similarities between clusters
      val sims = pairwiseClusterSimilarities(h.clusters, other.clusters)
      // find matches greedily (because Jaccard similarity is symmetric)
      val similaritySum = sumGreedyMatchedDists(sims)
      similaritySum / n
    }
  }

  protected def pairwiseClusterSimilarities[T <: BitSet | BloomFilter[Int]](clusters1: Array[T], clusters2: Array[T]): Array[Array[Double]] = {
    val n = clusters1.length
    val sims = Array.ofDim[Double](n, n)
    var i = 0
    while i < n do
      var j = i
      while j < n do
        val d = (clusters1(i), clusters2(j)) match
          case (x: BloomFilter[Int] @unchecked, y: BloomFilter[Int] @unchecked) => JaccardSimilarity(x, y)
          case (x: BitSet, y: BitSet) => JaccardSimilarity(x, y)
        sims(i)(j) = d
        if i != j then
          sims(j)(i) = d
        j += 1
      i += 1
    sims
  }

  protected def sumGreedyMatchedDists(dists: Array[Array[Double]]): Double = {
    val n = dists.length
    var similaritySum = 0.0
    val matched = mutable.BitSet.empty
    matched.sizeHint(n - 1)
    var i = 0
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
    similaritySum
  }
}
