package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, PostStop}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol.{DistanceMatrix, RegisterApproxDistMatrixReceiver}
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyParameters.InternalStrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol.{GetTSIndexMapping, TSIndexMappingResponse}
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.structures.strategies.ApproxDistanceWorkGenerator.Direction
import de.hpi.fgis.dendrotime.structures.strategies.{ApproxDistanceWorkGenerator, WorkGenerator}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object ApproxDistanceStrategy {

  private case class TSIndexMapping(mapping: Map[TsId, Int]) extends StrategyCommand
  private case class ApproxDistances(dists: PDist) extends StrategyCommand
  private case class WorkGenCreated(queue: WorkGenerator[TsId]) extends StrategyCommand

  object Ascending extends StrategyFactory {
    override def apply(params: InternalStrategyParameters): Behavior[StrategyCommand] =
      new ApproxDistanceStrategy(params, Direction.Ascending).start()
  }

  object Descending extends StrategyFactory {
    override def apply(params: InternalStrategyParameters): Behavior[StrategyCommand] =
      new ApproxDistanceStrategy(params, Direction.Descending).start()
  }
}

class ApproxDistanceStrategy private(params: InternalStrategyParameters, direction: Direction)
  extends Strategy(params) with ProcessedTrackingMixin {

  import ApproxDistanceStrategy.*

  private val tsIndexMappingAdapter = ctx.messageAdapter[TSIndexMappingResponse](m => TSIndexMapping(m.mapping))
  private val approxDistancesAdapter = ctx.messageAdapter[DistanceMatrix](m => ApproxDistances(m.distances))
  // Executor for internal futures (CPU-heavy work)
  private given ExecutionContext = ctx.system.dispatchers.lookup(DispatcherSelector.blocking())

  override def start(): Behavior[StrategyCommand] = {
    if fallbackWorkGenerator.hasNext then
      params.tsManager ! GetTSIndexMapping(params.dataset.id, tsIndexMappingAdapter)
      params.clusterer ! RegisterApproxDistMatrixReceiver(approxDistancesAdapter)
      collecting(None, None)
    else
      Behaviors.stopped
  }

  private def collecting(mapping: Option[Map[TsId, Int]], dists: Option[PDist]): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(_) => Behaviors.same // ignore

    case m: DispatchWork => dispatchFallbackWork(m)

    case TSIndexMapping(mapping) =>
      ctx.log.debug("Received TS Index Mapping: {}", mapping.size)
      potentiallyBuildQueue(Some(mapping), dists)

    case ApproxDistances(dists) =>
      ctx.log.debug(s"Received approximate distances", dists.n)
      potentiallyBuildQueue(mapping, Some(dists))

    case WorkGenCreated(queue) =>
      if queue.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      ctx.log.info("Received work queue of size {} ({} already processed), serving", queue.sizeTuples, processed.size)
      stash.unstashAll(serving(queue))

    case ReportStatus =>
      ctx.log.info("[REPORT] Preparing, {} fallback work items already processed", processed.size)
      Behaviors.same
  }

  private def serving(workGen: WorkGenerator[TsId]): Behavior[StrategyCommand] = Behaviors.receiveMessagePartial[StrategyCommand] {
    case AddTimeSeries(_) =>
      // ignore
      Behaviors.same

    case DispatchWork(worker, time, size) if workGen.hasNext =>
      val batchSize = nextBatchSize(time, size)
      val work = workGen.nextBatch(batchSize, processed.contains)
      ctx.log.trace("Dispatching full job ({}) remaining={}, Stash={}", work.length, workGen.remaining, stash.size)
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

  private def potentiallyBuildQueue(mapping: Option[Map[TsId, Int]], dists: Option[PDist]): Behavior[StrategyCommand] = {
    (mapping, dists) match {
      case (Some(m), Some(d)) =>
        val size = d.n * (d.n - 1) / 2 - processed.size
        ctx.log.info("Received both approximate distances and mapping, building work Queue of size {} ({} already processed)", size, processed.size)
        val f = Future { ApproxDistanceWorkGenerator[TsId](m, processed, d, direction) }
        ctx.pipeToSelf(f) {
          case Success(queue) => WorkGenCreated(queue)
          case Failure(e) => throw e
        }
      case _ =>
    }
    collecting(mapping, dists)
  }
}
