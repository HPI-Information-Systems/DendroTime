package de.hpi.fgis.dendrotime.distances

object MSM {
  extension (d: Double) {
    private def roundTo(decimals: Int): Double = {
      val factor = Math.pow(10, decimals)
      (d * factor).round / factor
    }
  }
  
  val DEFAULT_COST: Double = 1.0
  val DEFAULT_WINDOW: Double = Double.NaN
  val DEFAULT_ITAKURA_MAX_SLOPE: Double = 0.8
  
  private def createBoundingMatrix(n: Int, m: Int, window: Double = DEFAULT_WINDOW, itakuraMaxSlope: Double = DEFAULT_ITAKURA_MAX_SLOPE): Array[Array[Boolean]] = {
    if itakuraMaxSlope.isFinite && window.isFinite then
      throw new IllegalArgumentException("itakuraMaxSlope and window cannot be set at the same time")
    if itakuraMaxSlope.isFinite then
      if !(0 < itakuraMaxSlope && itakuraMaxSlope < 1) then
        throw new IllegalArgumentException("itakuraMaxSlope must be between 0 and 1")
      itakuraParallelogram(n, m, itakuraMaxSlope)
    else if window.isFinite then
      if !(0 < window && window < 1) then
        throw new IllegalArgumentException("window must be between 0 and 1")
      sakoeChibaBounding(n, m, window)
    else
      Array.tabulate(n, m)((_, _) => true)
  }

  private def sakoeChibaBounding(n: Int, m: Int, window: Double): Array[Array[Boolean]] = {
    val onePercent = Math.min(n, m) / 100.0
    val radius = (window * onePercent * 100).floor.toInt
    val boundingMatrix = Array.tabulate(n, m)((_, _) => false)

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

  private def itakuraParallelogram(n: Int, m: Int, itakuraMaxSlope: Double): Array[Array[Boolean]] = {
    val onePercent = Math.min(n, m) / 100.0
    var maxSlope = Math.floor((itakuraMaxSlope * onePercent) * 100)
    var minSlope = 1 / maxSlope
    maxSlope *= n.toDouble / m
    minSlope *= n.toDouble / m

    val lowerBound1 = Array.tabulate(m)(i => i * minSlope)
    val lowerBound2 = Array.tabulate(m)(i => (n - 1) - maxSlope * (m - 1) + maxSlope * i)

    val lowerBound = Array.ofDim[Int](m)
    for i <- 0 until m do
      val bound = Math.max(lowerBound1(i).roundTo(2), lowerBound2(i).roundTo(2))
      lowerBound(i) = bound.ceil.toInt

    val upperBound1 = Array.tabulate(m)(i => i * maxSlope)
    val upperBound2 = Array.tabulate(m)(i => (n - 1) - minSlope * (m - 1) + minSlope * i)

    val upperBound = Array.ofDim[Int](m)
    for i <- 0 until m do
      val bound = Math.min(upperBound1(i).roundTo(2), upperBound2(i).roundTo(2))
      upperBound(i) = (bound + 1).ceil.toInt

    val boundingMatrix = Array.tabulate(n, m)((_, _) => false)
    for i <- 0 until m do
      for x <- boundingMatrix.slice(lowerBound(i), upperBound(i)) do
        x(i) = true
    boundingMatrix
  }
}

class MSM(
           val c: Double = MSM.DEFAULT_COST,
           val window: Double = MSM.DEFAULT_WINDOW,
           val itakuraMaxSlope: Double = MSM.DEFAULT_ITAKURA_MAX_SLOPE
         ) extends Distance {

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
          distances(i)(j) = apply(x(i), x(j))
          distances(j)(i) = distances(i)(j)

      distances

  override def multiPairwise(x: Array[Array[Double]], y: Array[Array[Double]]): Array[Array[Double]] =
    x.map(xi => y.map(yi => apply(xi, yi)))

  private def fastPairwiseDistance(x: Array[Array[Double]], n_timesteps: Int) = {
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

  override def apply(x: Array[Double], y: Array[Double]): Double = {
    if x.length == 0 || y.length == 0 then
      return 0.0

    val boundingMatrix = MSM.createBoundingMatrix(x.length, y.length, window, itakuraMaxSlope)
    val currentCostMatrix = costMatrix(x, y, boundingMatrix, c)
    currentCostMatrix(x.length - 1)(y.length - 1)
  }

  private def costMatrix(x: Array[Double], y: Array[Double], boundingMatrix: Array[Array[Boolean]], c: Double): Array[Array[Double]] = {
    val n = x.length
    val m = y.length
    val costMatrix = Array.ofDim[Double](n, m)
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

          costMatrix(i)(j) = Seq(cost1, cost2, cost3).min
    costMatrix
  }

  private def independentCost(x: Double, y: Double, z: Double, c: Double): Double = {
    if (y <= x && x <= z) || (y >= x && x >= z) then
      c
    else
      c + Math.min(Math.abs(x - y), Math.abs(x - z))
  }
}
