package de.hpi.fgis.dendrotime.actors.coordinator

import akka.actor.typed.ActorSystem
import de.hpi.fgis.dendrotime.Settings

trait AdaptiveBatchingMixin(system: ActorSystem[Nothing]) {
  private val targetDuration: Long = Settings(system).batchingTargetTime.toNanos
  private val decay: Double = 0.9
  private val maxBatchSize: Int = Settings(system).batchingMaxBatchSize.getOrElse(Int.MaxValue)

  private var meanItemTime: Long = 0
  private var totalNoItems: Long = 0
  private var currentBatchSize: Int = Math.min(maxBatchSize, 4)
  // statistics
  private var duration: Long = 0
  private var meanBatchSize: Int = 0
  private var batchUpdates: Int = 0

  def nextBatchSize(lastDuration: Long, lastBatchSize: Int): Int = {
    val batchMean = lastDuration / lastBatchSize
    val oldItemTime = meanItemTime
    if lastDuration != 0 then
      totalNoItems += lastBatchSize
      // simple moving average
//      meanItemTime = oldItemTime + (lastBatchSize * batchMean - lastBatchSize * oldItemTime) / totalNoItems
      // exponential weighted moving average
      meanItemTime = (oldItemTime + decay * (batchMean - oldItemTime)).toLong
      // calculate new batch size, but limit its growth to 2x (exponential growth)
      currentBatchSize = Math.max(
        1,
        Math.min(
          maxBatchSize,
          Math.min(2 * currentBatchSize, (targetDuration / meanItemTime).toInt)
        )
      )
      // update stats
      batchUpdates += 1
      meanBatchSize = meanBatchSize + (currentBatchSize - meanBatchSize) / batchUpdates
      duration = lastDuration

    currentBatchSize
  }

  def getBatchStats: String = {
    val stats = s"meanBatchSize: $meanBatchSize (over $batchUpdates batches), " +
      s"meanItemTime: ${meanItemTime / 1000}µs, " +
      s"lastDuration: ${duration / 1_000}µs (target: ${targetDuration / 1_000}µs)"
    meanBatchSize = 0
    batchUpdates = 0
    stats
  }
}
