// Code adapted from SciPy's implementation of hierarchical clustering.
// https://github.com/scipy/scipy/tree/main/scipy/cluster

package de.hpi.fgis.dendrotime.clustering.hierarchy

import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy.HierarchyBuilder

import scala.util.boundary
import scala.util.boundary.break

private[hierarchy] object NNChain {
  /**
   * Only use with reducible distances and linkage functions. Does also work for non-metric distance measures.
   * The distance is not required to satisfy the triangle inequality.
   *
   * @param dists   pairwise distances in condensed form
   * @param linkage linkage function
   */
  def apply(dists: PDist, linkage: Linkage, adjustLabels: Boolean = false): Hierarchy = {
    val n = dists.n
    val z = Hierarchy.newBuilder(n)
    val sizes = Array.fill(n)(1)
    val d = dists.mutableCopy

    // nn chain
    val chain = Array.ofDim[Int](n)
    var chainLength = 0

    for k <- 0 until n - 1 do
      if chainLength == 0 then
        chainLength = 1
        chain(0) = sizes.indexWhere(_ > 0)

      // find next nearest neighbors (mutual neighbors) in chain
      val result = nextNearestNeighbors(chain, sizes, d, chainLength)
      var x = result._1
      var y = result._2
      val distXY = result._3
      chainLength = result._4

      // Merge clusters x and y and pop them from stack
      chainLength -= 2
      if x > y then
        val tmp = x
        x = y
        y = tmp

      val nx = sizes(x)
      val ny = sizes(y)
      z.add(x, y, distXY) // , nx+ny)
      // drop cluster x
      sizes(x) = 0
      // replace cluster y with new cluster
      sizes(y) = nx + ny

      // update distances
      sizes
        .lazyZip(sizes.indices)
        .withFilter((ni, i) => ni != 0 && i != y)
        .foreach { (ni, i) =>
          d(i, y) = linkage(d(i, x), d(i, y), distXY, nx, ny, ni)
        }

    z.sort()

    if adjustLabels then
      LinkageUnionFind(n)(z)

    z.build()
  }

  /**
   * Find the next nearest neighbors (mutual neighbors) in the chain.
   */
  @inline
  private def nextNearestNeighbors(chain: Array[Int], sizes: Array[Int], d: PDist, initialLength: Int): (Int, Int, Double, Int) =
    boundary {
      var x = 0
      var y = 0
      var currentMin = Double.PositiveInfinity
      var chainLength = initialLength
      while true do
        x = chain(chainLength - 1)

        // We want to prefer the previous element in the chain as the
        // minimum, to avoid potentially going in cycles.
        if chainLength > 1 then
          y = chain(chainLength - 2)
          currentMin = d(x, y)
        else
          currentMin = Double.PositiveInfinity

        for i <- sizes.indices do
          if sizes(i) != 0 && x != i then
            val dist = d(x, i)
            if dist < currentMin then
              currentMin = dist
              y = i

        if chainLength > 1 && y == chain(chainLength - 2) then
          break((x, y, currentMin, chainLength))

        chain(chainLength) = y
        chainLength += 1
      (x, y, currentMin, chainLength)
    }

  private object LinkageUnionFind {
    /**
     * Label clusters (with consecutive integers starting from n) in a sorted hierarchy.
     *
     * @param n number of observations (existing clusters)
     */
    def apply(n: Int)(z: HierarchyBuilder): Unit = {
      val uf = new LinkageUnionFind(n)

      for i <- 0 until n-1 do
        val node = z(i)
        val x = uf.find(node.elem1)
        val y = uf.find(node.elem2)
        val size = uf.merge(x, y)
        if x < y then
          z.update(i, node.copy(elem1 = x, elem2 = y))
        else
          z.update(i, node.copy(elem1 = y, elem2 = x)) // , cardinality = size)
    }
  }

  /** Helper for union-find data structure. */
  private class LinkageUnionFind private(n: Int) {
    private val parents = (0 until 2*n-1).toArray
    private val sizes = Array.fill(2*n-1)(1)
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
}
