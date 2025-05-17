package de.hpi.fgis.dendrotime.clustering.distances

import de.hpi.fgis.dendrotime.clustering.distances.DistanceOptions.LorentzianOptions
import org.apache.commons.math3.util.FastMath


object Lorentzian {

  /** Normalize the time series to unit length. */
  @inline
  private final def normalize(x: Array[Double]): Array[Double] = {
    val norm = FastMath.sqrt(x.map(xi => xi * xi).sum)
    if (norm == 0) x else x.map(_ / norm)
  }

  val DEFAULT_NORMALIZE: Boolean = true

  given defaultOptions: LorentzianOptions = LorentzianOptions(normalize = DEFAULT_NORMALIZE)

  def create(using opt: LorentzianOptions): Lorentzian = Lorentzian(opt.normalize)

  def apply(normalize: Boolean = DEFAULT_NORMALIZE): Lorentzian = new Lorentzian(normalize)

  def unapply(distance: Lorentzian): Option[Boolean] = Some(distance.normalize)
}

/** Compute the Lorentzian distance between two time series.
 *
 * The Lorentzian distance is an L1 normalization.
 * For two time series `x` and `y` of length `n`, the Lorentzian distance is defined as:
 * ```math
 * ld(x, y) = \sum_{i=1}^{n} ln\left(1 + |x_i - y_i| \right)
 * ```
 *
 * If the two time series are of different length, the distance is computed only for the first `min(n, m)` elements.
 *
 * @constructor Create a new configured instance to compute the Lorentzian distance.
 * @param normalize Whether the time series should be normalized to unit length before computing the distance.
 * @example
 * ```scala
 * val ld = Lorentzian(p = 2)
 * val x = Array[Double](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
 * val y = Array[Double](11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
 * val dist = ld(x, y)
 * ```
 */
class Lorentzian private(val normalize: Boolean = Lorentzian.DEFAULT_NORMALIZE) extends Distance {

  /** Compute the Lorentzian distance between two time series `x` and `y`.
   *
   * @param x First time series, univariate of shape ``(n_timepoints,)``
   * @param y Second time series, univariate of shape ``(m_timepoints,)``
   * @return The Lorentzian distance between `x` and `y` using the first ``min(n_timepoints, m_timepoints)`` of
   *         each time series.
   * @see [[pairwise]] Compute the Lorentzian distance between all pairs of time series.
   * @see [[multiPairwise]] Compute the Lorentzian distance between pairs of two time series collections.
   * */
  override def apply(x: Array[Double], y: Array[Double]): Double = {
    if x.length == 0 || y.length == 0 then
      return 0.0

    val (xNorm, yNorm) =
      if normalize then
        Lorentzian.normalize(x) -> Lorentzian.normalize(y)
      else
        x -> y

    var distance = 0.0
    for (i <- 0 until FastMath.min(xNorm.length, yNorm.length)) do
      val absdiff = FastMath.abs(xNorm(i) - yNorm(i))
      distance += FastMath.log(1 + absdiff)

    distance
  }

  override def toString: String = s"Lorentzian(normalize=$normalize)"
}
