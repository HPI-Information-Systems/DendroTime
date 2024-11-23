package de.hpi.fgis.dendrotime.actors.coordinator

import akka.actor.typed.ActorSystem
import de.hpi.fgis.dendrotime.Settings

trait AdaptiveBatchingMixin(system: ActorSystem[Nothing]) {
  private val targetDuration: Long = Settings(system).batchingTargetTime.toNanos

  private var meanItemTime: Long = 0
  private var totalNoItems: Long = 0
  private var currentBatchSize: Int = 8

  def nextBatchSize(lastDuration: Long, lastBatchSize: Int): Int = {
    val oldItemTime = meanItemTime
    if lastDuration != 0 then
      // FIXME: I need a decay because the initial runtime estimations are way off (too small batches)
      // update batch time counters
      totalNoItems += lastBatchSize
      meanItemTime = oldItemTime + (lastDuration/lastBatchSize - oldItemTime) / totalNoItems

      // calculate new batch size, but limit it to 2x the current size
      val newBatchSize = Math.max(1, Math.min(2*currentBatchSize, (targetDuration / meanItemTime).toInt))
      currentBatchSize = newBatchSize

    println(s"Batch size: $currentBatchSize (meanItemTime: ${meanItemTime/1_000_000}ms, " +
      s"targetDuration: ${targetDuration/1_000_000}ms, totalNoItems: $totalNoItems, " +
      s"lastDuration: ${lastDuration/1_000_000}ms, lastBatchSize: $lastBatchSize)")
    currentBatchSize
  }
}
