package de.hpi.fgis.dendrotime.clustering.distances

object DTW {
  // FIXME:  val DEFAULT_WINDOW: Double = 0.05
  val DEFAULT_WINDOW: Double = 0.1
  val DEFAULT_ITAKURA_MAX_SLOPE: Double = Double.NaN
}

/** Compute the DTW distance between two time series.
 *
 * DTW is the most widely researched and used elastic distance measure. It mitigates
 * distortions in the time axis by re-aligning (warping) the series to best match
 * each other. A good background into DTW can be found in [1]_. For two series,
 * possibly of unequal length,
 * :math:`\mathbf{x}=\{x_1,x_2,\ldots,x_n\}` and
 * :math:`\mathbf{y}=\{y_1,y_2, \ldots,y_m\}` DTW first calculates
 * :math:`M(\mathbf{x},\mathbf{y})`, the :math:`n \times m`
 * point-wise distance matrix between series :math:`\mathbf{x}` and :math:`\mathbf{y}`,
 * where :math:`M_{i,j}=   (x_i-y_j)^2`.
 *
 * A warping path
 *
 * .. math::
 *     P = <(e_1, f_1), (e_2, f_2), \ldots, (e_s, f_s)>
 *
 * is a set of pairs of indices that  define a traversal of matrix :math:`M`. A
 * valid warping path must start at location :math:`(1,1)` and end at point :math:`(
 * n,m)` and not backtrack, i.e. :math:`0 \leq e_{i+1}-e_{i} \leq 1` and :math:`0
 * \leq f_{i+1}- f_i \leq 1` for all :math:`1 < i < m`.
 *
 * The DTW distance between series is the path through :math:`M` that minimizes the
 * total distance. The distance for any path :math:`P` of length :math:`s` is
 *
 * .. math::
 *     D_P(\mathbf{x},\mathbf{y}, M) =\sum_{i=1}^s M_{e_i,f_i}
 *
 * If :math:`\mathcal{P}` is the space of all possible paths, the DTW path :math:`P^*`
 * is the path that has the minimum distance, hence the DTW distance between series is
 *
 * .. math::
 *     d_{dtw}(\mathbf{x}, \mathbf{x}) =D_{P*}(\mathbf{x},\mathbf{x}, M).
 *
 * The optimal warping path :math:`P^*` can be found exactly through a dynamic
 * programming formulation. This can be a time consuming operation, and it is common to
 * put a restriction on the amount of warping allowed. This is implemented through
 * the bounding_matrix structure, that supplies a mask for allowable warpings.
 * The most common bounding strategies include the Sakoe-Chiba band [2]_. The width
 * of the allowed warping is controlled through the ``window`` parameter
 * which sets the maximum proportion of warping allowed.
 *
 * @constructor Create a new configured instance to compute the DTW distance.
 * @param window          Window size for the Sakoe-Chiba bounding method. Default is 0.1.
 * @param itakuraMaxSlope Maximum slope as a proportion of the number of time points used to create
 *                        Itakura parallelogram on the bounding matrix. Must be between 0. and 1. Default is NaN.
 * @note Paper references:
 *       [1] Ratanamahatana C and Keogh E.: Three myths about dynamic time warping data
 *       mining, Proceedings of 5th SIAM International Conference on Data Mining, 2005.
 *       [2] Sakoe H. and Chiba S.: Dynamic programming algorithm optimization for
 *       spoken word recognition. IEEE Transactions on Acoustics, Speech, and Signal
 *       Processing 26(1):43–49, 1978.
 * @example
 * ```scala
 * val dtw = DTW(window = 0.1)
 * val x = Array[Double](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
 * val y = Array[Double](11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
 * val dist = dtw(x, y)
 * ```
 */
class DTW(
           val window: Double = DTW.DEFAULT_WINDOW,
           val itakuraMaxSlope: Double = DTW.DEFAULT_ITAKURA_MAX_SLOPE
         ) extends Distance {

  /** Compute the DTW distance between two time series `x` and `y`.
   *
   * @param x First time series, univariate of shape ``(n_timepoints,)``
   * @param y Second time series, univariate of shape ``(m_timepoints,)``
   * @return The DTW distance between `x` and `y`.
   * @see [[pairwise]] Compute the DTW distance between all pairs of time series.
   * @see [[multiPairwise]] Compute the DTW distance between pairs of two time series collections.
   * */
  override def apply(x: Array[Double], y: Array[Double]): Double = {
    if x.length == 0 || y.length == 0 then
      return 0.0

    val boundingMatrix = Bounding.createBoundingMatrix(x.length, y.length, window, itakuraMaxSlope)
    val currentCostMatrix = costMatrix(x, y, boundingMatrix)
    currentCostMatrix(x.length - 1)(y.length - 1)
  }


  /** Compute the DTW distance between all pairs of time series in `x`.
   *
   * @param x Time series collection of shape ``(n_instances, n_timepoints)``.
   *          The time series could be of unequal length.
   * @return The DTW distances between all pairs of time series in `x` in an array of shape ``(n_instances, n_instances)``.
   *         The diagonal of the returned matrix is 0. The matrix is symmetric.
   * @see [[apply]] Compute the DTW distance between two time series.
   * @see [[multiPairwise]] Compute the DTW distance between pairs of two time series collections.
   */
  override def pairwise(x: Array[Array[Double]]): Array[Array[Double]] =
    val lengths = x.map(_.length).toSet
    if lengths.size == 1 then
      val n_timesteps = lengths.head
      fastPairwiseDistance(x, n_timesteps)
    else
      super.pairwise(x)

  override def toString: String = s"DTW(window=$window, itakuraMaxSlope=$itakuraMaxSlope)"

  @inline
  private final def fastPairwiseDistance(x: Array[Array[Double]], n_timesteps: Int) = {
    val n_instances = x.length
    val distances = Array.ofDim[Double](n_instances, n_instances)
    val boundingMatrix = Bounding.createBoundingMatrix(n_timesteps, n_timesteps, window, itakuraMaxSlope)

    for i <- 0 until n_instances do
      for j <- i + 1 until n_instances do
        val currentCostMatrix = costMatrix(x(i), x(j), boundingMatrix)
        val distance = currentCostMatrix(n_timesteps - 1)(n_timesteps - 1)
        distances(i)(j) = distance
        distances(j)(i) = distance

    distances
  }

  @inline
  private final def costMatrix(x: Array[Double], y: Array[Double], boundingMatrix: Array[Array[Boolean]]): Array[Array[Double]] = {
    val n = x.length
    val m = y.length
    val costMatrix = Array.fill[Double](n, m) {
      Double.PositiveInfinity
    }
    costMatrix(0)(0) = univariateSquared(x(0), y(0))

    for i <- 1 until n if boundingMatrix(i)(0) do
        costMatrix(i)(0) = costMatrix(i-1)(0) + univariateSquared(x(i), y(0))

    for j <- 1 until m if boundingMatrix(0)(j) do
      costMatrix(0)(j) = costMatrix(0)(j-1) + univariateSquared(x(0), y(j))

    for i <- 1 until n do
      for j <- 1 until m do
        if boundingMatrix(i)(j) then
          val prevMin = Math.min(
            costMatrix(i-1)(j), Math.min(
            costMatrix(i)(j-1),
            costMatrix(i-1)(j-1)
          ))
          val current = univariateSquared(x(i), y(j))

          costMatrix(i)(j) = prevMin + current
    costMatrix
  }

  @inline
  private final def univariateSquared(x: Double, y: Double): Double = {
    val diff = x - y
    diff * diff
  }
}

