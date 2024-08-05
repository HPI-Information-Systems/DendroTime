package de.hpi.fgis.dendrotime.clustering.distances

trait Distance {
  def apply(x: Array[Double], y: Array[Double]): Double
  def pairwise(x: Array[Array[Double]]): Array[Array[Double]]
  def multiPairwise(x: Array[Array[Double]], y: Array[Array[Double]]): Array[Array[Double]]
}

object Distance {
  def apply(name: String): Distance = name.toLowerCase match {
    case "msm" => new MSM()
    case "sbd" => new SBD()
    case other => throw new IllegalArgumentException(s"Distance $other is not implemented. Use one of 'MSM' or 'SBD'")
  }
}