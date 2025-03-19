package de.hpi.fgis.dendrotime.clustering.hierarchy

import org.apache.commons.math3.util.FastMath

/**
 * Linkage methods for hierarchical clustering.
 *
 * A linkage function computes the distance from a cluster i to the new cluster xy after merging
 * cluster x and cluster y.
 */
sealed trait Linkage {
  /**
   * Compute the distance from a cluster i to the new cluster xy after merging cluster x and cluster y.
   * 
   * @param dXi Distance from cluster x to cluster i
   * @param dYi Distance from cluster y to cluster i
   * @param dXY Distance from cluster x to cluster y
   * @param nX Number of observations in cluster x
   * @param nY Number of observations in cluster y
   * @param nI Number of observations in cluster i
   * @return Distance from the new cluster xy to cluster i
   */
  def apply(dXi: Double, dYi: Double, dXY: Double, nX: Int, nY: Int, nI: Int): Double
}

object Linkage {
  def apply(method: String): Linkage = method match {
    case "single" => SingleLinkage
    case "complete" => CompleteLinkage
    case "average" => AverageLinkage
    case "ward" => WardLinkage
    case "weighted" => WeightedLinkage
    case "median" => throw new IllegalArgumentException("Median linkage is not reducible")
    case "centroid" => CentroidLinkage
    case _ => throw new IllegalArgumentException(s"Unknown linkage method: $method")
  }

  def unapply(linkage: Linkage): String = linkage match {
    case SingleLinkage => "single"
    case CompleteLinkage => "complete"
    case AverageLinkage => "average"
    case WardLinkage => "ward"
    case WeightedLinkage => "weighted"
    case MedianLinkage => "median"
    case CentroidLinkage => "centroid"
    case null => throw new IllegalArgumentException("Unknown linkage method")
  }

  case object SingleLinkage extends Linkage {
    override def apply(dXi: Double, dYi: Double, dXY: Double, nX: Int, nY: Int, nI: Int): Double =
      FastMath.min(dXi, dYi)
  }

  case object CompleteLinkage extends Linkage {
    override def apply(dXi: Double, dYi: Double, dXY: Double, nX: Int, nY: Int, nI: Int): Double =
      FastMath.max(dXi, dYi)
  }

  case object AverageLinkage extends Linkage {
    override def apply(dXi: Double, dYi: Double, dXY: Double, nX: Int, nY: Int, nI: Int): Double =
      (nX * dXi + nY * dYi) / (nX + nY)
  }

  case object WardLinkage extends Linkage {
    override def apply(dXi: Double, dYi: Double, dXY: Double, nX: Int, nY: Int, nI: Int): Double =
      val t = 1.0 / (nX + nY + nI)
      FastMath.sqrt(
        (nI + nX) * t * dXi * dXi +
          (nI + nY) * t * dYi * dYi -
          nI * t * dXY * dXY
      )
  }

  case object WeightedLinkage extends Linkage {
    override def apply(dXi: Double, dYi: Double, dXY: Double, nX: Int, nY: Int, nI: Int): Double =
      0.5 * (dXi + dYi)
  }

  /**
   * Median linkage does not satisfy the reducible condition; thus, we recommend against using it together with
   * the NN-chain algorithm in DendroTime.
   */
  case object MedianLinkage extends Linkage {
    override def apply(dXi: Double, dYi: Double, dXY: Double, nX: Int, nY: Int, nI: Int): Double =
      FastMath.sqrt(
        0.5 * (dXi * dXi + dYi * dYi) - 0.25 * dXY * dXY
      )
  }

  /**
   * Centroid linkage does not satisfy the reducible condition; thus, we recommend against using it together with
   * the NN-chain algorithm in DendroTime.
   */
  case object CentroidLinkage extends Linkage {
    override def apply(dXi: Double, dYi: Double, dXY: Double, nX: Int, nY: Int, nI: Int): Double =
      FastMath.sqrt((
        (nX * dXi * dXi) + (nY * dYi * dYi) -
          (nX * nY * dXY * dXY) / (nX + nY)
        ) / (nX + nY))
  }
}
