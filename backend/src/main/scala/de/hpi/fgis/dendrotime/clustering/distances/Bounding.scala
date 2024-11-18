package de.hpi.fgis.dendrotime.clustering.distances

object Bounding {
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

  def createBoundingMatrix(n: Int, m: Int, window: Double, itakuraMaxSlope: Double): Array[Array[Boolean]] = {
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

//  @inline
//  private final def sakoeChibaBounding(n: Int, m: Int, window: Double): Array[Array[Boolean]] = {
//    val onePercent = Math.min(n, m) / 100.0
//    val radius = (window * onePercent * 100).floor.toInt
//    val boundingMatrix = Array.ofDim[Boolean](n, m)
//
//    val smallest = Math.min(n, m)
//    val largest = Math.max(n, m)
//    val width = largest - smallest + radius
//
//    for i <- 0 until smallest do
//      val lower = Math.max(0, i - radius)
//      val upper = Math.min(largest, i + width + 1)
//      for j <- lower until upper do
//        boundingMatrix(i)(j) = true
//    boundingMatrix
//  }

  @inline
  private final def sakoeChibaBounding(n: Int, m: Int, window: Double): Array[Array[Boolean]] = {
    val boundingMatrix = Array.ofDim[Boolean](n, m)

    val maxSize = Math.max(n, m) + 1
    val shortestDim = Math.min(n, m)
    val thickness = (window * shortestDim).floor.toInt

    for j <- 0 until maxSize do
      val lower = Math.max(0, (j.toDouble/maxSize*n).floor.toInt - thickness)
      val upper = Math.min(n, (j.toDouble/maxSize*n).floor.toInt + thickness + 1)
      val yIndex = (j.toDouble/maxSize*m).floor.toInt
      for i <- lower until upper do
        boundingMatrix(i)(yIndex) = true
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
