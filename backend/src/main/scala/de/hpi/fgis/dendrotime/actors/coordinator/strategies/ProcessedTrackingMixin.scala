package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.{DispatchWork, FullStrategyOutOfWork, StrategyCommand}
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.structures.CompactPairwiseBitset

trait ProcessedTrackingMixin { self: Strategy =>
  protected val processed: CompactPairwiseBitset = CompactPairwiseBitset.ofDim(params.timeseriesIds.length)

  override protected def dispatchFallbackWork(m: DispatchWork): Behavior[StrategyCommand] = {
    if fallbackWorkGenerator.hasNext then
      val batchSize = Math.max(nextBatchSize(m.lastJobDuration, m.lastBatchSize), 16)
      val work = fallbackWorkGenerator.nextBatch(batchSize)
      processed.addAll(work)
      ctx.log.trace("Dispatching full batch ({}) processedWork={}, Stash={}", batchSize, processed.size, stash.size)
      m.worker ! WorkerProtocol.CheckFull(work)
      Behaviors.same
    else
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", m.worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      stash.stash(m)
      Behaviors.same
  }
}
