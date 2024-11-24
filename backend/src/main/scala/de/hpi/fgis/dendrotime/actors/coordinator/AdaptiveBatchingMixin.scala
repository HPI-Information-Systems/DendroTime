package de.hpi.fgis.dendrotime.actors.coordinator

import akka.actor.typed.ActorSystem
import de.hpi.fgis.dendrotime.Settings

trait AdaptiveBatchingMixin(system: ActorSystem[Nothing]) {
  private val targetDuration: Long = Settings(system).batchingTargetTime.toNanos
  private val decay: Double = 0.9

  private var meanItemTime: Long = 0
  private var totalNoItems: Long = 0
  private var currentBatchSize: Int = 4

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
      currentBatchSize = Math.max(1, Math.min(2*currentBatchSize, (targetDuration / meanItemTime).toInt))

//    println(s"Batch size: $currentBatchSize (meanItemTime: ${meanItemTime/1000}µs (last:${batchMean/1_000}µs), " +
//      s"targetDuration: ${targetDuration/1_000}µs (lastDuration: ${lastDuration/1000}µs), " +
//      s"totalNoItems: $totalNoItems, lastBatchSize: $lastBatchSize)")
    currentBatchSize
  }
}
