package de.hpi.fgis.dendrotime.clustering.distances

trait Distance {
  def apply(x: Array[Double], y: Array[Double]): Double
  def pairwise(x: Array[Array[Double]]): Array[Array[Double]]
  def multiPairwise(x: Array[Array[Double]], y: Array[Array[Double]]): Array[Array[Double]]
}
