package de.hpi.fgis.dendrotime.clustering.hierarchy

import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy.HierarchyBuilder


private[hierarchy] object LinkageUnionFind {
  /**
   * Label clusters (with consecutive integers starting from n) in a sorted hierarchy.
   *
   * @param n number of observations (existing clusters)
   */
  def apply(n: Int)(z: HierarchyBuilder): Unit = {
    val uf = new LinkageUnionFind(n)

    for i <- 0 until n - 1 do
      val node = z(i)
      val x = uf.find(node.elem1)
      val y = uf.find(node.elem2)
      val size = uf.merge(x, y)
      if x < y then
        z.update(i, node.copy(elem1 = x, elem2 = y, cardinality = size))
      else
        z.update(i, node.copy(elem1 = y, elem2 = x, cardinality = size))
  }
}

/** Helper that provides the union-find data structure. */
private class LinkageUnionFind private(n: Int) {
  private val parents = (0 until 2 * n - 1).toArray
  private val sizes = Array.fill(2 * n - 1)(1)
  private var nextLabel = n

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
