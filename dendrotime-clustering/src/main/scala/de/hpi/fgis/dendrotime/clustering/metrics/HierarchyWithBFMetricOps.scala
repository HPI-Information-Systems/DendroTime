package de.hpi.fgis.dendrotime.clustering.metrics

import de.hpi.fgis.dendrotime.clustering.hierarchy.HierarchyWithBF

object HierarchyWithBFMetricOps {
  def apply(hierarchy: HierarchyWithBF): HierarchyWithBFMetricOps = new HierarchyWithBFMetricOps(hierarchy)

  given Conversion[HierarchyWithBF, HierarchyWithBFMetricOps] = HierarchyWithBFMetricOps.apply(_)
}

final class HierarchyWithBFMetricOps(hierarchyBf: HierarchyWithBF) extends HierarchyMetricOps(hierarchyBf.hierarchy) {

  /**
   * Computes the Jaccard similarity between this hierarchy and another hierarchy based on representing each
   * cluster by its contained time series. The similarity is computed as the Jaccard similarity between the
   * sets of cluster representations.
   *
   * @param other          the other hierarchy with clusters represented as BloomFilters
   * @param cardLowerBound the lower bound for the cardinality of clusters to consider
   *                       (inclusive, default: 3)
   * @param cardUpperBound the upper bound for the cardinality of clusters to consider
   *                       (inclusive, default: 1 = at most n-1 elements)
   * @return the similarity
   */
  def similarity(other: HierarchyWithBF, cardLowerBound: Int, cardUpperBound: Int): Double =
    computeBoundedJaccardSimilarity(hierarchyBf.clusters, other.clusters, cardUpperBound, cardLowerBound)

  /**
   * Computes the weighted similarity between this hierarchy and another hierarchy.
   *
   * The weighted similarity is the average Jaccard similarity between all clusters of the two hierarchies. The
   * cluster matches are chosen greedily to maximize the similarity.
   *
   * @param other the other hierarchy with clusters represented as BloomFilters
   * @return the weighted similarity
   */
  def weightedSimilarity(other: HierarchyWithBF): Double = {
    val n = hierarchyBf.length
    // compute pairwise similarities between clusters
    val sims = pairwiseClusterSimilarities(hierarchyBf.clusters, other.clusters)
    // find matches greedily (because Jaccard similarity is symmetric)
    val similaritySum = sumGreedyMatchedDists(sims)
    similaritySum / n
  }
}
