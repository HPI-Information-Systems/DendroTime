package de.hpi.fgis.dendrotime.clustering

import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage.*

package object hierarchy {

  def computeHierarchy(dists: PDist, linkage: Linkage): Hierarchy = linkage match {
    case SingleLinkage => MST(dists, adjustLabels = true)
    case l => NNChain(dists, l, adjustLabels = true)
  }
}

