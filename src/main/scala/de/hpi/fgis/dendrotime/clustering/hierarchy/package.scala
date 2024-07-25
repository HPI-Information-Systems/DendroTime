package de.hpi.fgis.dendrotime.clustering

import de.hpi.fgis.dendrotime.clustering.distances.MSM
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage.*

import scala.util.boundary
import scala.util.boundary.break

package object hierarchy {

  def apply(dists: PDist, linkage: Linkage): Hierarchy = linkage match {
    case SingleLinkage => singleLinkageHierarchyMST(dists)
    case l => ???
  }

  /**
   * Only use with metric distances!!
   *
   * @param dists pairwise distances in condensed form
   * @return hierarchical clustering hierarchy
   */
  private def singleLinkageHierarchyMST(dists: PDist): Hierarchy = {
    val n = dists.length
    val z = Hierarchy.newBuilder(n)
    val merged = Array.fill(n)(false)
    val d = Array.fill(n)(Double.PositiveInfinity)

    var y = 0
    for k <- 0 until (n - 1) do {
      val x = y
      var currentMin = Double.PositiveInfinity
      merged(x) = true
      for i <- 0 until n do {
        if ! merged(i) then {
          val dist = dists(x, i)
          if d(i) > dist then
            d(i) = dist

          if d(i) < currentMin then
            y = i
          currentMin = d(i)
        }
      }
      z.add(x, y, currentMin)
    }

    z.build()
  }
}

