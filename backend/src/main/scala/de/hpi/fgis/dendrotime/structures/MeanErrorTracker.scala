package de.hpi.fgis.dendrotime.structures

import scala.collection.mutable

class MeanErrorTracker(nTrackers: Int, default: => Double = Double.MaxValue) extends mutable.IndexedSeq[Double] {
  private val mean = Array.fill(nTrackers)(0.0)
  private val updates = Array.fill(nTrackers)(0)

  override def update(i: Int, value: Double): Unit = {
    val oldMean = mean(i)
    val currentUpdates = updates(i) + 1
    mean(i) = oldMean + (value - oldMean) / currentUpdates
    updates(i) += currentUpdates
  }

  override def apply(i: Int): Double =
    if updates(i) == 0 then default
    else mean(i)

  override def length: Int = nTrackers
}
