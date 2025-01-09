package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.AdaptiveBatchingMixin
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.structures.strategies.GrowableFCFSWorkGenerator

object FCFSStrategy extends StrategyFactory {
  def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5

      Behaviors.withStash(stashSize) { stash =>
        new FCFSStrategy(ctx, stash, eventReceiver).start()
      }
    }
}

class FCFSStrategy private(ctx: ActorContext[StrategyCommand],
                           stash: StashBuffer[StrategyCommand],
                           eventReceiver: ActorRef[StrategyEvent]
                          ) extends AdaptiveBatchingMixin(ctx.system) {

  private val workGenerator = GrowableFCFSWorkGenerator.empty[TsId]

  def start(): Behavior[StrategyCommand] = running()

  private def running(): Behavior[StrategyCommand] = Behaviors.receiveMessage[StrategyCommand] {
    case AddTimeSeries(timeseriesIds) =>
      workGenerator.addAll(timeseriesIds)
      if workGenerator.hasNext then
        stash.unstashAll(Behaviors.same)
      else
        Behaviors.same

    case DispatchWork(worker, time, size) if workGenerator.hasNext =>
      val batchSize = nextBatchSize(time, size)
      val work = workGenerator.nextBatch(batchSize)
      ctx.log.trace("Dispatching full batch ({}), Stash={}", batchSize, stash.size)
      worker ! WorkerProtocol.CheckFull(work)
      Behaviors.same

    case m: DispatchWork =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", m.worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      stash.stash(m)
      Behaviors.same

    case ReportStatus =>
      ctx.log.info(
        "[REPORT] Serving, {}/{} work items remaining, {}",
        workGenerator.remaining, workGenerator.sizeTuples, getBatchStats
      )
      Behaviors.same
  } receiveSignal {
    case (_, PostStop) =>
      ctx.log.info(
        "[REPORT] Serving, {}/{} work items remaining, {}",
        workGenerator.remaining, workGenerator.sizeTuples, getBatchStats
      )
      Behaviors.same
  }
}
