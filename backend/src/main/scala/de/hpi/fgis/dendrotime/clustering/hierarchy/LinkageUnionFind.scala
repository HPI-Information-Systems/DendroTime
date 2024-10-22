package de.hpi.fgis.dendrotime.clustering.hierarchy

import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy.HierarchyBuilder


private[hierarchy] object LinkageUnionFind {
  /**
   * Label clusters (with consecutive integers starting from n) in a sorted hierarchy.
   *
   * @param n number of observations (existing clusters)
   */
  def apply(n: Int): HierarchyBuilder => Unit = new LinkageUnionFind(n).apply

  /**
   * Label clusters (with consecutive integers starting from n) in a sorted hierarchy.
   *
   * @param n number of observations (existing clusters)
   * @param z sorted hierarchy
   */
  def apply(n: Int, z: HierarchyBuilder): Unit = new LinkageUnionFind(n).apply(z)
}

/** Helper that provides the union-find data structure. */
private class LinkageUnionFind private(n: Int) {
  private val parents = (0 until 2 * n - 1).toArray
  private val sizes = Array.fill(2 * n - 1)(1)
  private var nextLabel = n

  private[LinkageUnionFind] def apply(z: HierarchyBuilder): Unit = {
    for i <- 0 until n - 1 do
      val node = z(i)
      val x = find(node.cId1)
      val y = find(node.cId2)
//      if x == y then
//        throw new RuntimeException(s"x=$x and y=$y are equal! i=$i, z=$z")
      val size = merge(x, y)
      if x < y then
        z.update(i, node.copy(cId1 = x, cId2 = y, cardinality = size))
      else
        z.update(i, node.copy(cId1 = y, cId2 = x, cardinality = size))
  }

  private def find(x: Int): Int = {
    var p = x
    var root = x

    while parents(root) != root do
      root = parents(root)

    while parents(p) != root do
      p = parents(p)
      parents(p) = root

    root
  }

  private def merge(x: Int, y: Int): Int = {
    parents(x) = nextLabel
    parents(y) = nextLabel
    val size = sizes(x) + sizes(y)
    sizes(nextLabel) = size
    nextLabel += 1
    size
  }
}
