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
    var i = 0
    while i < n_instances do
      var j = i + 1
      while j < n_instances do
        distances(i)(j) = apply(x(i), x(j))
        distances(j)(i) = distances(i)(j)
        j += 1
      i += 1

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
  given defaultOptions: DistanceOptions = DistanceOptions(
    msm = MSM.defaultOptions,
    dtw = DTW.defaultOptions,
    sbd = SBD.defaultOptions,
    minkowsky = Minkowsky.defaultOptions,
    lorentzian = Lorentzian.defaultOptions
  )

  def apply(name: String)(using DistanceOptions): Distance = name.toLowerCase match {
    case "msm" => MSM.create
    case "sbd" => SBD.create
    case "dtw" => DTW.create
    case "manhatten" => Minkowsky(1)
    case "euclidean" => Minkowsky(2)
    case "minkowsky" => Minkowsky.create
    case "lorentzian" => Lorentzian.create
    case other => throw new IllegalArgumentException(s"Distance $other is not implemented. Use one of 'MSM' or 'SBD'")
  }

  def unapply(distance: Distance): String = distance match {
    case _: MSM => "msm"
    case _: SBD => "sbd"
    case _: DTW => "dtw"
    case Minkowsky(1) => "manhatten"
    case Minkowsky(2) => "euclidean"
    case _: Minkowsky => "minkowsky"
    case _: Lorentzian => "lorentzian"
    case _ => throw new IllegalArgumentException("Unknown distance type")
  }
}