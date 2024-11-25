package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.AdaptiveBatchingMixin
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.structures.strategies.{GrowableFCFSWorkGenerator, ShortestTsWorkGenerator, WorkGenerator}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object ShortestTsStrategy extends StrategyFactory {
  
  private case class TSLengthsResponse(lengths: Map[Long, Int]) extends StrategyCommand
  
  private case class WorkGenCreated(generator: WorkGenerator[Long]) extends StrategyCommand

  def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5
  
      Behaviors.withStash(stashSize) { stash =>
        new ShortestTsStrategy(ctx, stash, eventReceiver, params).start()
      }
    }
}

class ShortestTsStrategy private(ctx: ActorContext[StrategyCommand],
                                 stash: StashBuffer[StrategyCommand],
                                 eventReceiver: ActorRef[StrategyEvent],
                                 params: StrategyParameters
                                ) extends AdaptiveBatchingMixin(ctx.system) {

  import ShortestTsStrategy.*

  private val tsAdapter = ctx.messageAdapter[TsmProtocol.TSLengthsResponse](m => TSLengthsResponse(m.lengths))
  private val fallbackWorkGenerator = GrowableFCFSWorkGenerator.empty[Long]
  // Executor for internal futures (CPU-heavy work)
  private given ExecutionContext = ctx.system.dispatchers.lookup(DispatcherSelector.blocking())

  def start(): Behavior[StrategyCommand] = {
    params.tsManager ! TsmProtocol.GetTSLengths(params.dataset.id, tsAdapter)
    collecting(Set.empty)
  }

  private def collecting(processedWork: Set[(Long, Long)]): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      fallbackWorkGenerator.addAll(timeseriesIds)
      if fallbackWorkGenerator.hasNext then
        stash.unstashAll(Behaviors.same)
      else
        Behaviors.same

    case TSLengthsResponse(lengths) =>
      val f = Future { ShortestTsWorkGenerator[Long](lengths) }
      ctx.pipeToSelf(f){
        case Success(generator) => WorkGenCreated(generator)
        case Failure(e) => throw e
      }
      ctx.log.debug("Received lengths of {} time series, building work Queue", lengths.size)
      Behaviors.same

    case WorkGenCreated(workGen) =>
      if workGen.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      ctx.log.info("Received work queue of size {} ({} already processed), serving", workGen.sizeTuples, processedWork.size)
      stash.unstashAll(serving(workGen, processedWork))

    case m@DispatchWork(worker, time, size) =>
      if fallbackWorkGenerator.hasNext then
        val batchSize = Math.max(nextBatchSize(time, size), 16)
        val work = fallbackWorkGenerator.nextBatch(batchSize)
        ctx.log.trace("Dispatching full job ({}) processedWork={}, Stash={}", work.length, processedWork.size, stash.size)
        worker ! WorkerProtocol.CheckFull(work)
        collecting(processedWork ++ work)
      else
        ctx.log.debug("Worker {} asked for work but there is none (stash={})", worker, stash.size)
        if stash.isEmpty then
          eventReceiver ! FullStrategyOutOfWork
        stash.stash(m)
        Behaviors.same

    case ReportStatus =>
      ctx.log.info("[REPORT] Preparing, {} fallback work items already processed", processedWork.size)
      Behaviors.same
  }
  
  private def serving(workGen: WorkGenerator[Long], processedWork: Set[(Long, Long)]): Behavior[StrategyCommand] = Behaviors.receiveMessagePartial {
    case AddTimeSeries(_) =>
      // ignore
      Behaviors.same

    case DispatchWork(worker, time, size) if workGen.hasNext =>
      val batchSize = nextBatchSize(time, size)
      val work = workGen.nextBatch(batchSize, processedWork)
      ctx.log.trace("Dispatching full job ({} items), stash={}", work.length, stash.size)
      worker ! WorkerProtocol.CheckFull(work)
      Behaviors.same

    case m @ DispatchWork(worker, _, _) =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      stash.stash(m)
      Behaviors.same

    case ReportStatus =>
      ctx.log.info(
        "[REPORT] Serving, {}/{} work items remaining, {}",
        workGen.remaining, workGen.sizeTuples, getBatchStats
      )
      Behaviors.same
  }
}
