// Code adapted from SciPy's implementation of hierarchical clustering.
// https://github.com/scipy/scipy/tree/main/scipy/cluster

package de.hpi.fgis.dendrotime.clustering.hierarchy

import de.hpi.fgis.dendrotime.clustering.PDist

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
    var k = 0
    while k < n - 1 do
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
      z.add(x, y, distXY, nx+ny)
      // drop cluster x
      sizes(x) = 0
      // replace cluster y with new cluster
      sizes(y) = nx + ny

      // update distances
      var i = 0
      while i < sizes.length do
        val ni = sizes(i)
        if ni != 0 && i != y then
          val newDist = linkage(d(i, x), d(i, y), distXY, nx, ny, ni)
          d(i, y) = if newDist.isNaN then Double.PositiveInfinity else newDist
        i += 1

      k += 1

    z.sort()

    if adjustLabels then
      LinkageUnionFind(n, z)

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
      // start with +INF - 1 to avoid adding the non-existing distances, which are set to +INF
      var currentMin = Double.PositiveInfinity - 1
      var chainLength = initialLength
      while true do
        x = chain(chainLength - 1)

        // We want to prefer the previous element in the chain as the
        // minimum, to avoid potentially going in cycles.
        if chainLength > 1 then
          y = chain(chainLength - 2)
          currentMin = d(x, y)
        else
          currentMin = Double.PositiveInfinity - 1

        var i = 0
        while i < sizes.length do
          if sizes(i) != 0 && x != i then
            val dist = d(x, i)
            if dist <= currentMin then
              currentMin = dist
              y = i
          i += 1

        if chainLength > 1 && y == chain(chainLength - 2) then
          break((x, y, currentMin, chainLength))

        chain(chainLength) = y
        chainLength += 1
      (x, y, currentMin, chainLength)
    }
}
