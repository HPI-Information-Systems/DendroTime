package de.hpi.fgis.dendrotime.clustering.distances

object MSM {
  extension (d: Double) {
    /** Round a double to a given number of decimal places.
     *
     * @param decimals Number of decimal places to round to.
     * @return The rounded double.
     */
    @inline
    private def roundTo(decimals: Int): Double = {
      val factor = Math.pow(10, decimals)
      (d * factor).round / factor
    }
  }

  val DEFAULT_COST: Double = 0.5
// FIXME:  val DEFAULT_WINDOW: Double = 0.05
  val DEFAULT_WINDOW: Double = Double.NaN
  val DEFAULT_ITAKURA_MAX_SLOPE: Double = Double.NaN

  private def createBoundingMatrix(n: Int, m: Int, window: Double = DEFAULT_WINDOW, itakuraMaxSlope: Double = DEFAULT_ITAKURA_MAX_SLOPE): Array[Array[Boolean]] = {
    require(!(itakuraMaxSlope.isFinite && window.isFinite), "itakuraMaxSlope and window cannot be set at the same time")
    if itakuraMaxSlope.isFinite then
      require(0 < itakuraMaxSlope && itakuraMaxSlope < 1, "itakuraMaxSlope must be between 0 and 1")
      require(n == m, "itakuraMaxSlope can only be used for equal length time series")
      itakuraParallelogram(n, m, itakuraMaxSlope)
    else if window.isFinite then
      require(0 < window && window < 1, "window must be between 0 and 1")
      if n <= m then
        sakoeChibaBounding(n, m, window)
      else
        sakoeChibaBounding(m, n, window).transpose
    else
      Array.tabulate(n, m)((_, _) => true)
  }

  @inline
  private final def sakoeChibaBounding(n: Int, m: Int, window: Double): Array[Array[Boolean]] = {
    val onePercent = Math.min(n, m) / 100.0
    val radius = (window * onePercent * 100).floor.toInt
    val boundingMatrix = Array.ofDim[Boolean](n, m)

    val smallest = Math.min(n, m)
    val largest = Math.max(n, m)
    val width = largest - smallest + radius

    for i <- 0 until smallest do
      val lower = Math.max(0, i - radius)
      val upper = Math.min(largest, i + width + 1)
      for j <- lower until upper do
        boundingMatrix(i)(j) = true
    boundingMatrix
  }

  @inline
  private final def itakuraParallelogram(n: Int, m: Int, itakuraMaxSlope: Double): Array[Array[Boolean]] = {
    val onePercent = Math.min(n, m) / 100.0
    var maxSlope = Math.floor((itakuraMaxSlope * onePercent) * 100)
    var minSlope = 1 / maxSlope
    maxSlope *= n.toDouble / m
    minSlope *= n.toDouble / m

    @inline
    def computeBound(i: Int, upper: Boolean): Int =
      if upper then
        Math.min(
          (i * maxSlope).roundTo(2),
          ((n - 1) - minSlope * (m - 1) + minSlope * i).roundTo(2)
        ).floor.toInt + 1
      else
        Math.max(
          (i * minSlope).roundTo(2),
          ((n - 1) - maxSlope * (m - 1) + maxSlope * i).roundTo(2)
        ).ceil.toInt

    val boundingMatrix = Array.ofDim[Boolean](n, m)
    for i <- 0 until m do
      val lowerBound = computeBound(i, upper = false)
      val upperBound = computeBound(i, upper = true)
      for x <- boundingMatrix.slice(lowerBound, upperBound) do
        x(i) = true
    boundingMatrix
  }
}

/** Compute the MSM distance between two time series.
 *
 * Move-Split-Merge (MSM) [1]_ is a distance measure that is conceptually similar to
 * other edit distance-based approaches, where similarity is calculated by using a
 * set of operations to transform one series into another. Each operation has an
 * associated cost, and three operations are defined for MSM: move, split, and merge.
 * Move is called match in other distance function terminology and split and
 * merge are equivalent to insert and delete.
 *
 * For two series, possibly of unequal length, :math:`\mathbf{x}=\{x_1,x_2,\ldots, x_n\}`
 * and :math:`\mathbf{y}=\{y_1,y_2, \ldots,y_m\}` MSM works by iterating over
 * series lengths :math:`i = 1 \ldots n` and :math:`j = 1 \ldote m` to find the cost
 * matrix $D$ as follows:
 *
 * ```math
 * move  &= D_{i-1,j-1}+d({x_{i},y_{j}}) \\
 * split &= D_{i-1,j}+cost(y_j,y_{j-1},x_i,c) \\
 * merge &= D_{i,j-1}+cost(x_i,x_{i-1},y_j,c) \\
 * D_{i,j} &= min(move, split, merge)
 * ```
 *
 * Where :math:`D_{0,j}` and :math:`D_{i,0}` are initialised to a constant value,
 * and $c$ is a parameter that represents the cost of moving off the diagonal.
 * The point wise distance function $d$ is the absolute difference rather than the
 * squared distance.
 *
 * $cost$ is the cost function that calculates the cost of inserting and deleting
 * values. Crucially, the cost depends on the current and adjacent values,
 * rather than treating all insertions and deletions equally (for example,
 * as in ERP).
 *
 * ```math
 * cost(x,y,z,c) &= c & if\;\; & y \leq x \leq z \\
 * &= c & if\;\; & y \geq x \geq z \\
 * &= c+min(|x-y|,|x-z|) & & otherwise \\
 * ```
 *
 * MSM satisfies triangular inequality and is a metric.
 *
 * @constructor Create a new configured instance to compute the MSM distance.
 * @param c               Cost for split or merge operation. Default is 1.0.
 * @param window          Window size for the Sakoe-Chiba bounding method. Default is NaN.
 * @param itakuraMaxSlope Maximum slope as a proportion of the number of time points used to create
 *                        Itakura parallelogram on the bounding matrix. Must be between 0. and 1. Default is 0.8.
 * @note Paper reference: [1] Stefan A., Athitsos V., Das G.: The Move-Split-Merge metric for time
 *       series. IEEE Transactions on Knowledge and Data Engineering 25(6), 2013.
 * @example
 * ```scala
 * val msm = MSM(c = 1.0, itakuraMaxSlope = 0.9)
 * val x = Array[Double](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
 * val y = Array[Double](11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
 * val dist = msm(x, y)
 * ```
 */
class MSM(
           val c: Double = MSM.DEFAULT_COST,
           val window: Double = MSM.DEFAULT_WINDOW,
           val itakuraMaxSlope: Double = MSM.DEFAULT_ITAKURA_MAX_SLOPE
         ) extends Distance {

  /** Compute the MSM distance between two time series `x` and `y`.
   *
   * @param x First time series, univariate of shape ``(n_timepoints,)``
   * @param y Second time series, univariate of shape ``(m_timepoints,)``
   * @return The MSM distance between `x` and `y`.
   * @see [[pairwise]] Compute the Move-Split-Merge distance (MSM) between all pairs of time series.
   * @see [[multiPairwise]] Compute the Move-Split-Merge distance (MSM) between pairs of two time series collections.
   * */
  override def apply(x: Array[Double], y: Array[Double]): Double = {
    if x.length == 0 || y.length == 0 then
      return 0.0

    val boundingMatrix = MSM.createBoundingMatrix(x.length, y.length, window, itakuraMaxSlope)
    val currentCostMatrix = costMatrix(x, y, boundingMatrix, c)
    currentCostMatrix(x.length - 1)(y.length - 1)
  }


  /** Compute the MSM distance between all pairs of time series in `x`.
   *
   * @param x Time series collection of shape ``(n_instances, n_timepoints)``.
   *          The time series could be of unequal length.
   * @return The MSM distances between all pairs of time series in `x` in an array of shape ``(n_instances, n_instances)``.
   *         The diagonal of the returned matrix is 0. The matrix is symmetric.
   * @see [[apply]] Compute the Move-Split-Merge distance (MSM) between two time series.
   * @see [[multiPairwise]] Compute the Move-Split-Merge distance (MSM) between pairs of two time series collections.
   */
  override def pairwise(x: Array[Array[Double]]): Array[Array[Double]] =
    val lengths = x.map(_.length).toSet
    if lengths.size == 1 then
      val n_timesteps = lengths.head
      fastPairwiseDistance(x, n_timesteps)
    else
      val n_instances = x.length
      val distances = Array.ofDim[Double](n_instances, n_instances)
      for i <- 0 until n_instances do
        for j <- i + 1 until n_instances do
          val distance = apply(x(i), x(j))
          distances(i)(j) = distance
          distances(j)(i) = distance

      distances

  /** Compute the MSM distance between pairs of time series in `x` and `y`.
   *
   * @param x Time series collection of shape ``(n_instances, n_timepoints)``.
   * @param y Time series collection of shape ``(m_instances, m_timepoints)``.
   * @return The MSM distances between pairs of time series in `x` and `y` of shape ``(n_instances, m_instances)``.
   * @see [[apply]] Compute the Move-Split-Merge distance (MSM) between two time series.
   * @see [[pairwise]] Compute the Move-Split-Merge distance (MSM) between all pairs of time series.
   */
  override def multiPairwise(x: Array[Array[Double]], y: Array[Array[Double]]): Array[Array[Double]] =
    x.map(xi => y.map(yi => apply(xi, yi)))

  override def toString: String = s"MSM(c=$c, window=$window, itakuraMaxSlope=$itakuraMaxSlope)"

  @inline
  private final def fastPairwiseDistance(x: Array[Array[Double]], n_timesteps: Int) = {
    val n_instances = x.length
    val distances = Array.ofDim[Double](n_instances, n_instances)
    val boundingMatrix = MSM.createBoundingMatrix(n_timesteps, n_timesteps, window, itakuraMaxSlope)

    for i <- 0 until n_instances do
      for j <- i + 1 until n_instances do
        val currentCostMatrix = costMatrix(x(i), x(j), boundingMatrix, c)
        val distance = currentCostMatrix(n_timesteps - 1)(n_timesteps - 1)
        distances(i)(j) = distance
        distances(j)(i) = distance

    distances
  }

  @inline
  private final def costMatrix(x: Array[Double], y: Array[Double], boundingMatrix: Array[Array[Boolean]], c: Double): Array[Array[Double]] = {
    val n = x.length
    val m = y.length
    val costMatrix = Array.fill[Double](n, m) {
      Double.PositiveInfinity
    }
    costMatrix(0)(0) = Math.abs(x(0) - y(0))

    for i <- 1 until n do
      if boundingMatrix(i)(0) then
        val cost = independentCost(x(i), x(i - 1), y(0), c)
        costMatrix(i)(0) = costMatrix(i - 1)(0) + cost

    for j <- 1 until m do
      if boundingMatrix(0)(j) then
        val cost = independentCost(y(j), y(j - 1), x(0), c)
        costMatrix(0)(j) = costMatrix(0)(j - 1) + cost

    for i <- 1 until n do
      for j <- 1 until m do
        if boundingMatrix(i)(j) then
          val cost1 = costMatrix(i - 1)(j - 1) + Math.abs(x(i) - y(j))
          val cost2 = costMatrix(i - 1)(j) + independentCost(x(i), x(i - 1), y(j), c)
          val cost3 = costMatrix(i)(j - 1) + independentCost(y(j), x(i), y(j - 1), c)

          costMatrix(i)(j) = Math.min(cost1, Math.min(cost2, cost3))
    costMatrix
  }

  @inline
  private final def independentCost(x: Double, y: Double, z: Double, c: Double): Double = {
    if (y <= x && x <= z) || (y >= x && x >= z) then
      c
    else
      c + Math.min(Math.abs(x - y), Math.abs(x - z))
  }
}
