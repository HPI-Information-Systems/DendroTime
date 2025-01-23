// Code adapted from SciPy's implementation of hierarchical clustering.
// https://github.com/scipy/scipy/blob/v1.14.1/scipy/cluster/hierarchy.py#L1285-L1369

package de.hpi.fgis.dendrotime.clustering.hierarchy

import scala.collection.mutable
import scala.reflect.ClassTag

object CutTree {

  /**
   * Cut a hierarchical clustering tree (Hierarchy) at a certain height. The height is determined by
   * the desired number of clusters.
   *
   * @param hierarchy The hierarchical clustering tree.
   * @param nClusters The number of clusters to cut the tree at. Either a single integer or an array of integers.
   *                  If an array is provided, the tree is cut at the height of each index (number of clusters).
   * @tparam T The type of the number of clusters. Either an integer or an array of integers.
   * @return An array of T. If a single integer is provided, the array contains the cluster assignments for each
   *         observation. If an array of integers is provided, the array contains the cluster assignments for each
   *         observation at each height.
   */
  def apply[T <: Array[Int] | Int : ClassTag](hierarchy: Hierarchy, nClusters: T): Array[T] = {
    val _nClusters: Array[Int] = nClusters match {
      case n: Int => Array(n)
      case n: Array[Int] => n
    }
    val nobs = hierarchy.n
    val colsIdx = _nClusters.map(n => nobs - n)
    val nCols = _nClusters.length

    val groups = Array.ofDim[Int](nCols, nobs)
    var lastGroup = Array.tabulate(nobs)(i => i)
    if colsIdx.contains(0) then
      groups(0) = lastGroup

    else
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

        if colsIdx.contains(i + 1) then
          val idx = colsIdx.indexOf(i + 1)
          groups(idx) = newGroup
        lastGroup = newGroup
    if groups.length == 1 then
      groups(0).asInstanceOf[Array[T]]
    else
      groups.map(_.asInstanceOf[T])
}
  
  @inline
  private def buildCluster(node: Hierarchy.Node, clusters: Array[Array[Int]], nobs: Int): Array[Int] = {
    val cluster = Array.ofDim[Int](node.cardinality)
    var i = 0

    if node.cId1 < nobs then
      cluster(i) = node.cId1
      i += 1
    else
      val c1 = clusters(node.cId1 - nobs)
      System.arraycopy(c1, 0, cluster, i, c1.length)
      i += c1.length

    if node.cId2 < nobs then
      cluster(i) = node.cId2
      i += 1
    else
      val c2 = clusters(node.cId2 - nobs)
      System.arraycopy(c2, 0, cluster, i, c2.length)
    cluster
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
