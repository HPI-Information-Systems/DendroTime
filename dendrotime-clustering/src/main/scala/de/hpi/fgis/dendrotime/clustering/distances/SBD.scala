package de.hpi.fgis.dendrotime.clustering.distances

import de.hpi.fgis.dendrotime.clustering.distances.DistanceOptions.SBDOptions
import fftw3.{FFTWProvider, FFTWReal}
import org.apache.commons.math3.util.FastMath

object SBD extends DistanceFactory[SBD, SBDOptions] {
  @inline
  private final def standardize(x: Array[Double]): Array[Double] = {
    val xMean = x.sum / x.length
    val xStd = FastMath.sqrt(x.map(xi => FastMath.pow(xi - xMean, 2)).sum / x.length)
    x.map(xi => (xi - xMean) / xStd)
  }

  val DEFAULT_STANDARDIZE: Boolean = false

  given defaultOptions: SBDOptions = SBDOptions(standardize = DEFAULT_STANDARDIZE)

  override def create(using opt: SBDOptions): SBD = SBD(opt.standardize, opt.localFftwCacheSize)
}

/** Compute the shape-based distance (SBD) between two time series.
 *
 * Shape-based distance (SBD) [1]_ is a normalized version of cross-correlation (CC) that is shifting
 * and scaling (if standardization is used) invariant.
 *
 * For two series, possibly of unequal length, :math:`\mathbf{x}=\{x_1,x_2,\ldots,x_n\}`
 * and :math:`\mathbf{y}=\{y_1,y_2, \ldots,y_m\}`, SBD works by (optionally)
 * first standardizing both time series using the z-score
 * (:math:`x' = \frac{x - \mu}{\sigma}`), then computing the cross-correlation
 * between x and y (:math:`CC(\mathbf{x}, \mathbf{y})`), then deviding it by the
 * geometric mean of both autocorrelations of the individual sequences to normalize
 * it to :math:`[-1, 1]` (coefficient normalization), and finally detecting the
 * position with the maximum normalized cross-correlation:
 *
 * ```math
 * SBD(\mathbf{x}, \mathbf{y}) = 1 - max_w\left( \frac{
 * CC_w(\mathbf{x}, \mathbf{y})
 * }{
 * \sqrt{ (\mathbf{x} \cdot \mathbf{x}) * (\mathbf{y} \cdot \mathbf{y}) }
 * }\right)
 * ```
 *
 * This distance measure has values between 0 and 2; 0 is perfect similarity.
 *
 * The computation of the cross-correlation :math:`CC(\mathbf{x}, \mathbf{y})` for
 * all values of w requires :math:`O(m^2)` time, where m is the maximum time-series
 * length. We can however use the convolution theorem to our advantage, and use the
 * fast (inverse) fourier transform (FFT) to perform the computation of
 * :math:`CC(\mathbf{x}, \mathbf{y})` in :math:`O(m \cdot log(m))`:
 *
 * ```math
 * CC(x, y) = \mathcal{F}^{-1}\{
 * \mathcal{F}(\mathbf{x}) * \mathcal{F}(\mathbf{y})
 * \}
 * ```
 *
 *
 * @constructor Create a new configured instance to compute the SBD distance.
 * @param standardize Apply z-score to both input time series for standardization before computing the distance.
 *                    This makes SBD scaling invariant. Default is True.
 * @note Paper reference: Paparrizos, John, and Luis Gravano: Fast and Accurate Time-Series
 *       Clustering. ACM Transactions on Database Systems 42, no. 2 (2017):
 *       8:1-8:49. https://doi.org/10.1145/3044711.
 * @example
 * ```scala
 * val sbd = SBD(standardize = true)
 * val x = Array[Double](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
 * val y = Array[Double](11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
 * val dist = sbd(x, y)
 * ```
 */
class SBD(val standardize: Boolean = SBD.DEFAULT_STANDARDIZE, localFftwCacheSize: Option[Int] = None) extends Distance with AutoCloseable {

  given fftwProvider: FFTWProvider = localFftwCacheSize match {
    case Some(cacheSize) => FFTWProvider.localCaching(cacheSize)
    case None => FFTWProvider.defaultFfftwProvider
  }

  /** Compute the SBD distance between two time series `x` and `y`.
   *
   * @param x First time series, univariate of shape ``(n_timepoints,)``
   * @param y Second time series, univariate of shape ``(m_timepoints,)``
   * @return The SBD distance between `x` and `y`.
   * @see [[pairwise]] Compute the shape-based distance (SBD) between all pairs of time series.
   * @see [[multiPairwise]] Compute the shape-based distance (SBD) between pairs of two time series collections.
   * */
  override def apply(x: Array[Double], y: Array[Double]): Double = {
    if x.length == 0 || y.length == 0 then
      return 0.0

    val (xStd, yStd) = if standardize then
      if x.length == 1 || y.length == 1 then
        return 0.0

      SBD.standardize(x) -> SBD.standardize(y)
    else
      x -> y

    // FIXME: avoid re-allocating a new FFTWReal instance for each call
    val a = FFTWReal.fftwConvolve(xStd, yStd)
    val b = FastMath.sqrt(xStd.map(xi => xi * xi).sum * yStd.map(yi => yi * yi).sum)
    FastMath.abs(1.0 - a.max / b)
  }

  override def toString: String = s"SBD(standardize=$standardize, fftwProvider=$fftwProvider)"

  override def close(): Unit = {
    fftwProvider match {
      case p: FFTWProvider.LocalCachingFFTWProvider => p.close()
      case _ =>
    }
  }
}
