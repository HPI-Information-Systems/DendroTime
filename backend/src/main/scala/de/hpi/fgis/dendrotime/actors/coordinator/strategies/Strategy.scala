package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyParameters.InternalStrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.structures.strategies.GrowableFCFSWorkGenerator

abstract class Strategy(protected val params: InternalStrategyParameters) extends AdaptiveBatchingMixin(params.ctx.system) {

  protected val fallbackWorkGenerator: GrowableFCFSWorkGenerator[TsId] = GrowableFCFSWorkGenerator(params.timeseriesIds)

  def start(): Behavior[StrategyCommand]

  protected def ctx: ActorContext[StrategyCommand] = params.ctx

  protected def stash: StashBuffer[StrategyCommand] = params.stash

  protected def eventReceiver: ActorRef[StrategyEvent] = params.eventReceiver

  protected def dispatchFallbackWork(m: DispatchWork): Behavior[StrategyCommand] = {
    if fallbackWorkGenerator.hasNext then
      val batchSize = Math.max(nextBatchSize(m.lastJobDuration, m.lastBatchSize), 16)
      val work = fallbackWorkGenerator.nextBatch(batchSize)
      ctx.log.trace("Dispatching full batch ({}), Stash={}", batchSize, stash.size)
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
