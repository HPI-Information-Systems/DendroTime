package de.hpi.fgis.dendrotime.structures

object WelfordsAlgorithm {
  case class WelfordResult private[WelfordsAlgorithm](mean: Double, variance: Double, sampleVariance: Double, n: Int) {
    lazy val stdDev: Double = Math.sqrt(variance)

    override def toString: String =
      s"WelfordResult(mean=$mean, variance=$variance, sampleVariance=$sampleVariance, n=$n, stdDev=$stdDev)"
  }

  /** Computes the mean, variance, and sample variance in a single-pass over the array (online)!
   *
   * This algorithm is known as Welford's algorithm and is less prone to loss of precision than the naive approach.
   *
   * @param data The data array
   * @return The mean, variance, sample variance, and number of elements
   * */
  def apply(data: Array[Double]): WelfordResult = {
    var mean = 0.0 // tracks the mean
    var m2 = 0.0 // tracks the sum of squares of differences

    var i = 0
    while i < data.length do
      val x = data(i)
      i += 1
      // first update mean
      val delta = x - mean
      mean += delta / i
      // then update m2
      val delta2 = x - mean
      m2 += delta * delta2

    if i < 2 then
      WelfordResult(mean, Double.NaN, Double.NaN, i)
    else
      WelfordResult(mean, m2 / i, m2 / (i - 1), i)
  }
}
