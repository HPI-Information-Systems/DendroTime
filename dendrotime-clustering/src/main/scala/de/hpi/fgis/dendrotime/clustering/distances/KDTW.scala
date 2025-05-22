package de.hpi.fgis.dendrotime.clustering.distances

import de.hpi.fgis.dendrotime.clustering.distances.DistanceOptions.KDTWOptions
import org.apache.commons.math3.util.FastMath

object KDTW extends DistanceFactory[KDTW, KDTWOptions] {
  val DEFAULT_GAMMA: Double = 1.0
  val DEFAULT_EPSILON: Double = 1e-20
  val DEFAULT_NORMALIZE_INPUT: Boolean = true
  val DEFAULT_NORMALIZE_DISTANCE: Boolean = true

  private final val FACTOR: Double = 1.0 / 3.0

  given defaultOptions: KDTWOptions = KDTWOptions(
    DEFAULT_GAMMA, DEFAULT_EPSILON, DEFAULT_NORMALIZE_INPUT, DEFAULT_NORMALIZE_DISTANCE
  )

  override def create(using opt: KDTWOptions): KDTW = new KDTW(
    opt.gamma, opt.epsilon, opt.normalizeInput, opt.normalizeDistance
  )

  private final def normalizeTS(x: Array[Double]): Array[Double] = {
    val mean = x.sum / x.length
    val std = FastMath.sqrt(x.map(xi => (xi - mean) * (xi - mean)).sum / x.length)
    x.map(xi => (xi - mean) / (std + KDTW.DEFAULT_EPSILON))
  }
}

/** Compute the KDTW distance between two time series.
 *
 * @constructor Create a new configured instance to compute the KDTW distance.
 * @param gamma             bandwidth parameter, which weights the local contributions, i.e., the distances between
 *                          locally aligned positions. Must be greater than 0 and smaller than 1. Default is 0.125.
 * @param epsilon           small positive number to avoid numerical issues. Default is 1e-20.
 * @param normalizeInput    whether the input time series should be normalized to zero mean and unit variance.
 * @param normalizeDistance whether the distance should be normalized by the product of the self-distances of x and y
 *                          to avoid scaling effects and put the distance into the range [0, 1].
 * @note Paper references:
 *       [1] Pierre-François Marteau and Sylvie Gibet: On recursive edit distance kernels
 *       with application to time series classification. IEEE Transactions on Neural
 *       Networks and Learning Systems 26(6), 2014, pages 1121 - 1133.
 *       [2] Paparrizos, John, Chunwei Liu, Aaron J. Elmore, and Michael J. Franklin:
 *       Debunking Four Long-Standing Misconceptions of Time-Series Distance Measures. In
 *       Proceedings of the International Conference on Management of Data (SIGMOD),
 *       1887-1905, 2020. https://doi.org/10.1145/3318464.3389760.
 * @example
 * ```scala
 * val dtw = KDTW(gamma = 0.125)
 * val x = Array[Double](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
 * val y = Array[Double](11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
 * kdtw(x, y)
 * ```
 */
class KDTW(
            val gamma: Double = KDTW.DEFAULT_GAMMA,
            val epsilon: Double = KDTW.DEFAULT_EPSILON,
            val normalizeInput: Boolean = KDTW.DEFAULT_NORMALIZE_INPUT,
            val normalizeDistance: Boolean = KDTW.DEFAULT_NORMALIZE_DISTANCE
          ) extends Distance {

  require(gamma > 0.0, "gamma must be greater than 0!")

  /** Compute the KDTW distance between two time series `x` and `y`.
   *
   * @param x First time series, univariate of shape ``(n_timepoints,)``
   * @param y Second time series, univariate of shape ``(m_timepoints,)``
   * @return The KDTW distance between `x` and `y`.
   * @see [[pairwise]] Compute the KDTW distance between all pairs of time series.
   * @see [[multiPairwise]] Compute the KDTW distance between pairs of two time series collections.
   * */
  override def apply(x: Array[Double], y: Array[Double]): Double = {
    if x.length == 0 || y.length == 0 then
      0.0

    else if normalizeInput then
      val xNorm = KDTW.normalizeTS(x)
      val yNorm = KDTW.normalizeTS(y)
      kdtwDistance(xNorm, yNorm)

    else
      kdtwDistance(x, y)
  }

  /** Compute KDTW distances between all pairs of time series in `x`.
   *
   * @param x Time series collection of shape ``(n_instances, n_timepoints)``.
   *          The time series could be of unequal length.
   * @return The distances between all pairs of time series in `x` in an array of shape ``(n_instances, n_instances)``.
   *         The diagonal of the returned matrix is 0. The matrix is symmetric.
   * @see [[apply]] Compute the distance between two time series.
   * @see [[multiPairwise]] Compute distances between pairs of two time series collections.
   */
  override def pairwise(x: Array[Array[Double]]): Array[Array[Double]] = {
    // just normalize once
    if normalizeInput then
      val xNorm = x.map(KDTW.normalizeTS)
      internalPairwiseDistance(xNorm)
    else
      internalPairwiseDistance(x)
  }

  /** Compute KDTW distances between pairs of time series in `x` and `y`.
   *
   * @param x Time series collection of shape ``(n_instances, n_timepoints)``.
   * @param y Time series collection of shape ``(m_instances, m_timepoints)``.
   * @return The distances between pairs of time series in `x` and `y` of shape ``(n_instances, m_instances)``.
   * @see [[apply]] Compute the distance between two time series.
   * @see [[pairwise]] Compute distances between all pairs of time series.
   */
  override def multiPairwise(x: Array[Array[Double]], y: Array[Array[Double]]): Array[Array[Double]] = {
    // just normalize once
    val xNorm = if normalizeInput then x.map(KDTW.normalizeTS) else x
    val yNorm = if normalizeInput then y.map(KDTW.normalizeTS) else y
    xNorm.map(xi => yNorm.map(yi => kdtwDistance(xi, yi)))
  }

  override def toString: String =
    s"KDTW(gamma=$gamma, epsilon=$epsilon, normalizeInput=$normalizeInput, normalizeDistance=$normalizeDistance)"

  @inline
  private final def internalPairwiseDistance(x: Array[Array[Double]]) = {
    val n_instances = x.length
    val distances = Array.ofDim[Double](n_instances, n_instances)
    var i = 0
    while i < n_instances do
      var j = i + 1
      while j < n_instances do
        distances(i)(j) = kdtwDistance(x(i), x(j))
        distances(j)(i) = distances(i)(j)
        j += 1
      i += 1

    distances
  }

  @inline
  private final def kdtwDistance(x: Array[Double], y: Array[Double]): Double = {
    if x.length == 0 || y.length == 0 then
      0.0
    else
      val n = x.length - 1
      val m = y.length - 1
      var currentCost = costMatrix(x, y)(n)(m)

      if normalizeDistance then
        val selfDistanceX = costMatrix(x, x)(n)(n)
        val selfDistanceY = costMatrix(y, y)(m)(m)
        val normFactor = FastMath.sqrt(selfDistanceX * selfDistanceY)
        if normFactor != 0 then
          currentCost /= normFactor

      1.0 - currentCost
  }

  @inline
  private final def costMatrix(x: Array[Double], y: Array[Double]): Array[Array[Double]] = {
    // The local kernel is precomputed
    val localKernel = computeLocalKernel(x, y)
    // For the initial values of the cost matrix, we add 1
    val n = x.length + 1
    val m = y.length + 1
    val costMatrix = Array.ofDim[Double](n, m)
    val cdpDiag = Array.ofDim[Double](n, m)
    val diag = Array.ofDim[Double](FastMath.max(n, m))

    // Initialize the diagonal weights
    val minN = FastMath.min(n, m)
    diag(0) = 1.0
    var i = 1
    while i < minN do
      diag(i) = localKernel(i - 1)(i - 1)
      i += 1

    // Initialize the cost matrix and the cumulative DP diagonal
    costMatrix(0)(0) = 1.0
    cdpDiag(0)(0) = 1.0

    // - left column
    i = 1
    while i < n do
      costMatrix(i)(0) = costMatrix(i - 1)(0) * localKernel(i - 1)(0)
      cdpDiag(i)(0) = cdpDiag(i - 1)(0) * diag(i)
      i += 1

    // - top row
    var j = 1
    while j < m do
      costMatrix(0)(j) = costMatrix(0)(j - 1) * localKernel(0)(j - 1)
      cdpDiag(0)(j) = cdpDiag(0)(j - 1) * diag(j)
      j += 1

    // Perform the main dynamic programming loop
    i = 1
    while i < n do
      j = 1
      while j < m do
        val localCost = localKernel(i - 1)(j - 1)
        costMatrix(i)(j) = localCost * (
          costMatrix(i - 1)(j)
            + costMatrix(i)(j - 1)
            + costMatrix(i - 1)(j - 1)
          )
        cdpDiag(i)(j) = cdpDiag(i - 1)(j) * diag(i) + cdpDiag(i)(j - 1) * diag(j)
        if i == j then
          cdpDiag(i)(j) += localCost * cdpDiag(i - 1)(j - 1)
        j += 1
      i += 1

    // Add the cumulative dp diagonal to the cost matrix and remove top row and most left column
    val costMatrixFinal = Array.ofDim[Double](n - 1, m - 1)
    i = 1
    while i < n do
      j = 1
      while j < m do
        costMatrixFinal(i - 1)(j - 1) = costMatrix(i)(j) + cdpDiag(i)(j)
        j += 1
      i += 1

    costMatrixFinal
  }

  @inline
  private final def computeLocalKernel(x: Array[Double], y: Array[Double]): Array[Array[Double]] = {
    val n = x.length
    val m = y.length
    val localKernel = Array.ofDim[Double](n, m)

    var i = 0
    while i < n do
      var j = 0
      while j < m do
        localKernel(i)(j) = KDTW.FACTOR * (FastMath.exp(-univariateSquared(x(i), y(j)) / gamma) + epsilon)
        j += 1
      i += 1

    localKernel
  }

  @inline
  private final def univariateSquared(x: Double, y: Double): Double = {
    val diff = x - y
    diff * diff
  }
}

