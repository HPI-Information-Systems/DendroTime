package de.hpi.fgis.dendrotime.clustering.metrics

import de.hpi.fgis.bloomfilter.BloomFilter

object JaccardSimilarity {
  def apply[T](s1: scala.collection.Set[T], s2: scala.collection.Set[T]): Double = {
    val intersection = s1 & s2
    val union = s1 | s2
    intersection.size.toDouble / union.size
  }

  def apply(bf1: BloomFilter[Int], bf2: BloomFilter[Int]): Double = {
    val intersection = bf1 & bf2
    val union = bf1 | bf2
    val result = intersection.approximateElementCount.toDouble / union.approximateElementCount
    intersection.dispose()
    union.dispose()
    result
  }
}
