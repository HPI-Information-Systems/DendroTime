package de.hpi.fgis.dendrotime.clustering

import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage.*

package object hierarchy {

  def computeHierarchy(dists: PDist, linkage: Linkage): Hierarchy = linkage match {
    case SingleLinkage => singleLinkageHierarchyMST(dists, adjustLabels = true)
    case l => NNChain(dists, l, adjustLabels = true)
  }

  /**
   * Only use with metric distances!!
   *
   * Code adapted from SciPy's implementation of hierarchical clustering.
   * https://github.com/scipy/scipy/tree/main/scipy/cluster/_hierarchy.pyx
   *
   * @param dists pairwise distances in condensed form
   * @return hierarchical clustering hierarchy
   */
  private def singleLinkageHierarchyMST(dists: PDist, adjustLabels: Boolean = false): Hierarchy = {
    val n = dists.n
    val z = Hierarchy.newBuilder(n)
    val merged = Array.fill(n)(false)
    val d = Array.fill(n)(Double.PositiveInfinity)

    var y = 0
    for k <- 0 until n - 1 do {
      val x = y
      var currentMin = Double.PositiveInfinity
      merged(x) = true
      for i <- 0 until n do
        if ! merged(i) then
          val dist = dists(x, i)
          if d(i) > dist then
            d(i) = dist

          if d(i) < currentMin then
            y = i
            currentMin = d(i)

      z.add(x, y, currentMin)
    }

    z.sort()

    if adjustLabels then
      LinkageUnionFind(n)(z)

    z.build()
  }
}

