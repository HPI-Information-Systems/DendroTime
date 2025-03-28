package de.hpi.fgis.dendrotime.actors.coordinator.strategies

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

  protected def nextBatchSize(): Int = nextBatchSize(0L, 0)

  protected def nextBatchSize(lastDuration: Long, lastBatchSize: Int): Int = {
    if lastDuration != 0 && lastBatchSize != 0 then
      val batchMean = lastDuration / lastBatchSize
      val oldItemTime = meanItemTime
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

  protected def getBatchStats: String = {
    val stats = s"meanBatchSize: $meanBatchSize (over $batchUpdates batches), " +
      s"meanItemTime: ${meanItemTime / 1000}µs, " +
      s"lastDuration: ${duration / 1_000}µs (target: ${targetDuration / 1_000}µs)"
    meanBatchSize = 0
    batchUpdates = 0
    stats
  }
}
