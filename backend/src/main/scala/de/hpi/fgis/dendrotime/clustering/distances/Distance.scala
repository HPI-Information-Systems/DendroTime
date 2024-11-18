package de.hpi.fgis.dendrotime.clustering.distances

trait Distance {
  def apply(x: Array[Double], y: Array[Double]): Double

  /** Compute distances between all pairs of time series in `x`.
   *
   * @param x Time series collection of shape ``(n_instances, n_timepoints)``.
   *          The time series could be of unequal length.
   * @return The distances between all pairs of time series in `x` in an array of shape ``(n_instances, n_instances)``.
   *         The diagonal of the returned matrix is 0. The matrix is symmetric.
   * @see [[apply]] Compute the distance between two time series.
   * @see [[multiPairwise]] Compute distances between pairs of two time series collections.
   */
  def pairwise(x: Array[Array[Double]]): Array[Array[Double]] =
    val n_instances = x.length
    val distances = Array.ofDim[Double](n_instances, n_instances)
    for i <- 0 until n_instances do
      for j <- i + 1 until n_instances do
        distances(i)(j) = apply(x(i), x(j))
        distances(j)(i) = distances(i)(j)

    distances

  /** Compute distances between pairs of time series in `x` and `y`.
   *
   * @param x Time series collection of shape ``(n_instances, n_timepoints)``.
   * @param y Time series collection of shape ``(m_instances, m_timepoints)``.
   * @return The distances between pairs of time series in `x` and `y` of shape ``(n_instances, m_instances)``.
   * @see [[apply]] Compute the distance between two time series.
   * @see [[pairwise]] Compute distances between all pairs of time series.
   */
  def multiPairwise(x: Array[Array[Double]], y: Array[Array[Double]]): Array[Array[Double]] =
    x.map(xi => y.map(yi => apply(xi, yi)))
}

object Distance {
  def apply(name: String): Distance = name.toLowerCase match {
    case "msm" => new MSM()
    case "sbd" => new SBD()
    case "dtw" => new DTW()
    case other => throw new IllegalArgumentException(s"Distance $other is not implemented. Use one of 'MSM' or 'SBD'")
  }
  def unapply(distance: Distance): String = distance match {
    case _: MSM => "msm"
    case _: SBD => "sbd"
    case _: DTW => "dtw"
    case _ => throw new IllegalArgumentException("Unknown distance type")
  }
}