package de.hpi.fgis.dendrotime.distances

object SBD {

  private def standardize(x: Array[Double]): Array[Double] = {
    val xMean = x.sum / x.length
    val xStd = Math.sqrt(x.map(xi => Math.pow(xi - xMean, 2)).sum / (x.length - 1))
    x.map(xi => (xi - xMean) / xStd)
  }
}

class SBD(val standardize: Boolean = true) extends Distance {

  override def apply(x: Array[Double], y: Array[Double]): Double = {
    if standardize then
      if x.length == 1 || y.length == 1 then
        return 0.0

      val xStd = SBD.standardize(x)
      val yStd = SBD.standardize(y)
    // fixme: implement SBD
    0.0
  }

  override def pairwise(x: Array[Array[Double]]): Array[Array[Double]] = ???

  override def multiPairwise(x: Array[Array[Double]], y: Array[Array[Double]]): Array[Array[Double]] = ???
}
