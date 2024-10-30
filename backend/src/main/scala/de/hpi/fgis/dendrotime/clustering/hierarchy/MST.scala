package de.hpi.fgis.dendrotime.clustering.hierarchy

import de.hpi.fgis.dendrotime.clustering.PDist

private[hierarchy] object MST {
  /**
   * Use the minimum spanning tree (MST) algorithm to compute a single linkage hierarchy.
   *
   * Code adapted from SciPy's implementation of hierarchical clustering.
   * https://github.com/scipy/scipy/blob/30a92651acb845fea4458189f09c25fe26a58699/scipy/cluster/_hierarchy.pyx#L1028
   *
   * @param dists pairwise distances in condensed form
   * @return hierarchical clustering hierarchy
   */
  def apply(dists: PDist, adjustLabels: Boolean = false): Hierarchy = {
    val n = dists.n
    val z = Hierarchy.newBuilder(n)
    val merged = Array.fill(n)(false)
    val d = Array.fill(n)(Double.PositiveInfinity)

    var y = 0
    for _ <- 0 until n - 1 do
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

    z.sort()

    if adjustLabels then
      LinkageUnionFind(n)(z)

    z.build()
  }
}
