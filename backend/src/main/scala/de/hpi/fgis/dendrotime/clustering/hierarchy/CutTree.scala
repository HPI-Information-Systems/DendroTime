// Code adapted from SciPy's implementation of hierarchical clustering.
// https://github.com/scipy/scipy/blob/v1.14.1/scipy/cluster/hierarchy.py#L1285-L1369

package de.hpi.fgis.dendrotime.clustering.hierarchy

import scala.collection.mutable

object CutTree {

  /**
   * Cut a hierarchical clustering tree (Hierarchy) at a certain height. The height is determined by
   * the desired number of clusters.
   */
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
