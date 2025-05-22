package de.hpi.fgis.dendrotime.clustering.distances

import de.hpi.fgis.dendrotime.clustering.distances.DistanceOptions.MinkowskyOptions
import fftw3.FFTWReal

import scala.annotation.switch

object Minkowsky extends DistanceFactory[Minkowsky, MinkowskyOptions] {

  val DEFAULT_P: Int = 2

  given defaultOptions: MinkowskyOptions = MinkowskyOptions(p = DEFAULT_P)

  override def create(using opt: MinkowskyOptions): Minkowsky = Minkowsky(opt.p)

  def apply(p: Int = DEFAULT_P): Minkowsky = new Minkowsky(p)

  def unapply(distance: Minkowsky): Option[Int] = Some(distance.p)
}

/** Compute the Minkowsky distance between two time series.
 *
 * The Minkowsky distance is a generalization of the Euclidean distance (p=2) and the Manhattan distance (p=1).
 * For two time series `x` and `y` of length `n`, the Minkowsky distance with parameter `p` is defined as:
 * ```math
 * md(x, y, p) = \left( \sum_{i=1}^{n} |x_i - y_i|^p \right)^{\frac{1}{p}}
 * ```
 * 
 * If the two time series are of different length, the distance is computed only for the first `min(n, m)` elements.
 *
 * @constructor Create a new configured instance to compute the Minkowsky distance.
 * @param p The order of the norm of the difference (default is 2.0, which represents the Euclidean distance).
 * @example
 * ```scala
 * val minkowsky = Minkowsky(p = 2)
 * val x = Array[Double](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
 * val y = Array[Double](11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
 * val dist = minkowsky(x, y)
 * ```
 */
class Minkowsky private (val p: Int = Minkowsky.DEFAULT_P) extends Distance {

  /** Compute the Minkowsky distance between two time series `x` and `y`.
   *
   * @param x First time series, univariate of shape ``(n_timepoints,)``
   * @param y Second time series, univariate of shape ``(m_timepoints,)``
   * @return The Minkowsky distance between `x` and `y` using the first ``min(n_timepoints, m_timepoints)`` of
   *         each time series.
   * @see [[pairwise]] Compute the Minkowsky distance between all pairs of time series.
   * @see [[multiPairwise]] Compute the Minkowsky distance between pairs of two time series collections.
   * */
  override def apply(x: Array[Double], y: Array[Double]): Double = {
    if x.length == 0 || y.length == 0 then
      return 0.0

    val n = Math.min(x.length, y.length)
    var dist = 0.0
    var i = 0
    while i < n do
      dist += Math.pow(Math.abs(x(i) - y(i)), p)
      i += 1
    Math.pow(dist, 1.0 / p)
  }

  override def toString: String = (p: @switch) match {
    case 1 => "Manhattan"
    case 2 => "Euclidean"
    case o => s"Minkowsky(p=$o)"
  }
}
