package de.hpi.fgis.dendrotime.clustering.distances

import de.hpi.fgis.dendrotime.clustering.distances.SBD.DEFAULT_STANDARDIZE
import fftw3.FFTWReal

object SBD {

  @inline
  private final def standardize(x: Array[Double]): Array[Double] = {
    val xMean = x.sum / x.length
    val xStd = Math.sqrt(x.map(xi => Math.pow(xi - xMean, 2)).sum / x.length)
    x.map(xi => (xi - xMean) / xStd)
  }

  val DEFAULT_STANDARDIZE: Boolean = true
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
  *    SBD(\mathbf{x}, \mathbf{y}) = 1 - max_w\left( \frac{
  *        CC_w(\mathbf{x}, \mathbf{y})
  *    }{
  *        \sqrt{ (\mathbf{x} \cdot \mathbf{x}) * (\mathbf{y} \cdot \mathbf{y}) }
  *    }\right)
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
  *    CC(x, y) = \mathcal{F}^{-1}\{
  *        \mathcal{F}(\mathbf{x}) * \mathcal{F}(\mathbf{y})
  *    \}
  * ```
  *
  * @constructor Create a new configured instance to compute the SBD distance.
  * @param standardize Apply z-score to both input time series for standardization before computing the distance.
  *                    This makes SBD scaling invariant. Default is True.
  * @note Paper reference: Paparrizos, John, and Luis Gravano: Fast and Accurate Time-Series
  *       Clustering. ACM Transactions on Database Systems 42, no. 2 (2017):
  *       8:1-8:49. https://doi.org/10.1145/3044711.
  *
  * @example
  * ```scala
  * val sbd = SBD(standardize = true)
  * val x = Array[Double](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  * val y = Array[Double](11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
  * val dist = sbd(x, y)
  * ```
  */
class SBD(val standardize: Boolean = DEFAULT_STANDARDIZE) extends Distance {

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
      
    val a = FFTWReal.fftwConvolve(xStd, yStd)
    val b = Math.sqrt(xStd.map(xi => xi * xi).sum * yStd.map(yi => yi * yi).sum)
    Math.abs(1.0 - a.max / b)
  }

  /** Compute the SBD distance between all pairs of time series in `x`.
   *
   * @param x Time series collection of shape ``(n_instances, n_timepoints)``.
   *          The time series could be of unequal length.
   * @return The SBD distances between all pairs of time series in `x` in an array of shape ``(n_instances, n_instances)``.
   *         The diagonal of the returned matrix is 0. The matrix is symmetric.
   * @see [[apply]] Compute the shape-based distance (SBD) between two time series.
   * @see [[multiPairwise]] Compute the shape-based distance (SBD) between pairs of two time series collections.
   */
  override def pairwise(x: Array[Array[Double]]): Array[Array[Double]] =
    val n_instances = x.length
    val distances = Array.ofDim[Double](n_instances, n_instances)
    for i <- 0 until n_instances do
      for j <- i + 1 until n_instances do
        distances(i)(j) = apply(x(i), x(j))
        distances(j)(i) = distances(i)(j)

    distances

  /** Compute the SBD distance between pairs of time series in `x` and `y`.
   *
   * @param x Time series collection of shape ``(n_instances, n_timepoints)``.
   * @param y Time series collection of shape ``(m_instances, m_timepoints)``.
   * @return The SBD distances between pairs of time series in `x` and `y` of shape ``(n_instances, m_instances)``.
   * @see [[apply]] Compute the shape-based distance (SBD) between two time series.
   * @see [[pairwise]] Compute the shape-based distance (SBD) between all pairs of time series.
   */
  override def multiPairwise(x: Array[Array[Double]], y: Array[Array[Double]]): Array[Array[Double]] =
    x.map(xi => y.map(yi => apply(xi, yi)))
}
