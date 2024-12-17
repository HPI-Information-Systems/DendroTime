package de.hpi.fgis.dendrotime.clustering.metrics

object JaccardSimilarity {
  def apply[T](s1: scala.collection.Set[T], s2: scala.collection.Set[T]): Double = {
    val intersection = s1 & s2
    val union = s1 | s2
    intersection.size.toDouble / union.size
  }
}
