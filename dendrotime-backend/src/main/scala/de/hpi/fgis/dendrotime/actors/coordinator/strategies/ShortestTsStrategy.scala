package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, PostStop}
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyParameters.InternalStrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.structures.strategies.{ShortestTsWorkGenerator, WorkGenerator}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object ShortestTsStrategy extends StrategyFactory {

  private case class TSLengthsResponse(lengths: IndexedSeq[Int]) extends StrategyCommand

  private case class WorkGenCreated(generator: WorkGenerator[Int]) extends StrategyCommand

  override def apply(params: InternalStrategyParameters): Behavior[StrategyCommand] =
    new ShortestTsStrategy(params).start()
}

class ShortestTsStrategy private(params: InternalStrategyParameters)
  extends Strategy(params) with ProcessedTrackingMixin {

  import ShortestTsStrategy.*

  private val tsAdapter = ctx.messageAdapter[TsmProtocol.TSLengthsResponse](m => TSLengthsResponse(m.lengths))

  // Executor for internal futures (CPU-heavy work)
  private given ExecutionContext = ctx.system.dispatchers.lookup(DispatcherSelector.blocking())

  override def start(): Behavior[StrategyCommand] = {
    params.tsManager ! TsmProtocol.GetTSLengths(params.dataset.id, tsAdapter)
    collecting()
  }

  private def collecting(): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(_) => Behaviors.same // ignore

    case m: DispatchWork => dispatchFallbackWork(m)

    case TSLengthsResponse(lengths) =>
      val f = Future {
        ShortestTsWorkGenerator[Int](lengths.indices.zip(lengths).toMap)
      }
      ctx.pipeToSelf(f) {
        case Success(generator) => WorkGenCreated(generator)
        case Failure(e) => throw e
      }
      ctx.log.debug("Received lengths of {} time series, building work Queue", lengths.size)
      Behaviors.same

    case WorkGenCreated(workGen) =>
      if workGen.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      ctx.log.info("Received work queue of size {} ({} already processed), serving", workGen.sizeTuples, processed.size)
      stash.unstashAll(serving(workGen))

    case ReportStatus =>
      ctx.log.info("[REPORT] Preparing, {} fallback work items already processed", processed.size)
      Behaviors.same
  }

  private def serving(workGen: WorkGenerator[Int]): Behavior[StrategyCommand] = Behaviors.receiveMessagePartial[StrategyCommand] {

    case DispatchWork(worker, time, size) if workGen.hasNext =>
      val batchSize = nextBatchSize(time, size)
      val work = workGen.nextBatch(batchSize, processed.contains)
      ctx.log.trace("Dispatching full job ({} items), stash={}", work.length, stash.size)
      worker ! WorkerProtocol.CheckFull(work)
      Behaviors.same

    case m@DispatchWork(worker, _, _) =>
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

  } receiveSignal {
    case (_, PostStop) =>
      ctx.log.info(
        "[REPORT] Serving, {}/{} work items remaining, {}",
        workGen.remaining, workGen.sizeTuples, getBatchStats
      )
      Behaviors.same
  }
}
