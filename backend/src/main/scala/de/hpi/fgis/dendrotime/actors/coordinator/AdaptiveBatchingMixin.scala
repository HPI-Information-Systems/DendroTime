package de.hpi.fgis.dendrotime.actors.coordinator

trait AdaptiveBatchingMixin { this: Coordinator =>
  private val targetTime: Long = settings.batchingTargetTime.toMillis

  private var lastBatchTime: Long = 0
  private var lastBatchSize: Int = 0


}
