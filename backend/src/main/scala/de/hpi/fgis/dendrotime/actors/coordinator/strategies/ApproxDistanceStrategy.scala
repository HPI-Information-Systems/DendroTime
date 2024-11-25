package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol.{ApproxDistanceMatrix, RegisterApproxDistMatrixReceiver}
import de.hpi.fgis.dendrotime.actors.coordinator.AdaptiveBatchingMixin
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol.{GetTSIndexMapping, TSIndexMappingResponse}
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.structures.strategies.ApproxDistanceWorkGenerator.Direction
import de.hpi.fgis.dendrotime.structures.strategies.{ApproxDistanceWorkGenerator, GrowableFCFSWorkGenerator, WorkGenerator}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object ApproxDistanceStrategy {

  private case class TSIndexMapping(mapping: Map[Long, Int]) extends StrategyCommand
  private case class ApproxDistances(dists: PDist) extends StrategyCommand
  private case class WorkGenCreated(queue: WorkGenerator[Long]) extends StrategyCommand

  object Ascending extends StrategyFactory {
    def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
      start(params, eventReceiver, Direction.Ascending)
  }

  object Descending extends StrategyFactory {
    def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
      start(params, eventReceiver, Direction.Descending)
  }

  private def start(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent], direction: Direction): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5

      Behaviors.withStash(stashSize) { stash =>
        new ApproxDistanceStrategy(ctx, stash, eventReceiver, params, direction).start()
      }
    }
}

class ApproxDistanceStrategy private(ctx: ActorContext[StrategyCommand],
                                     stash: StashBuffer[StrategyCommand],
                                     eventReceiver: ActorRef[StrategyEvent],
                                     params: StrategyParameters,
                                     direction: Direction
                                    ) extends AdaptiveBatchingMixin(ctx.system) {

  import ApproxDistanceStrategy.*

  private val fallbackWorkGenerator = GrowableFCFSWorkGenerator.empty[Long]
  private val tsIndexMappingAdapter = ctx.messageAdapter[TSIndexMappingResponse](m => TSIndexMapping(m.mapping))
  private val approxDistancesAdapter = ctx.messageAdapter[ApproxDistanceMatrix](m => ApproxDistances(m.distances))
  // Executor for internal futures (CPU-heavy work)
  private given ExecutionContext = ctx.system.dispatchers.lookup(DispatcherSelector.blocking())

  def start(): Behavior[StrategyCommand] = {
    params.tsManager ! GetTSIndexMapping(params.dataset.id, tsIndexMappingAdapter)
    params.clusterer ! RegisterApproxDistMatrixReceiver(approxDistancesAdapter)
    collecting(Set.empty, None, None)
  }

  private def collecting(processedWork: Set[(Long, Long)], mapping: Option[Map[Long, Int]], dists: Option[PDist]): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      fallbackWorkGenerator.addAll(timeseriesIds.sorted)
      if fallbackWorkGenerator.hasNext then
        stash.unstashAll(Behaviors.same)
      else
        Behaviors.same

    case TSIndexMapping(mapping) =>
      ctx.log.debug("Received TS Index Mapping: {}", mapping.size)
      potentiallyBuildQueue(processedWork, Some(mapping), dists)

    case ApproxDistances(dists) =>
      ctx.log.debug(s"Received approximate distances", dists.n)
      potentiallyBuildQueue(processedWork, mapping, Some(dists))

    case WorkGenCreated(queue) =>
      if queue.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      ctx.log.info("Received work queue of size {} ({} already processed), serving", queue.sizeTuples, processedWork.size)
      stash.unstashAll(serving(queue, processedWork))

    case m@DispatchWork(worker, time, size) =>
      if fallbackWorkGenerator.hasNext then
        val batchSize = Math.max(nextBatchSize(time, size), 16)
        val work = fallbackWorkGenerator.nextBatch(batchSize)
        ctx.log.trace("Dispatching full job ({}) processedWork={}, Stash={}", work.length, processedWork.size, stash.size)
        worker ! WorkerProtocol.CheckFull(work)
        collecting(processedWork ++ work, mapping, dists)
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
  }

  private def potentiallyBuildQueue(processedWork: Set[(Long, Long)], mapping: Option[Map[Long, Int]], dists: Option[PDist]): Behavior[StrategyCommand] = {
    (mapping, dists) match {
      case (Some(m), Some(d)) =>
        val size = d.n * (d.n - 1) / 2 - processedWork.size
        ctx.log.info("Received both approximate distances and mapping, building work Queue of size {} ({} already processed)", size, processedWork.size)
        val f = Future { ApproxDistanceWorkGenerator[Long](m, processedWork, d, direction) }
        ctx.pipeToSelf(f) {
          case Success(queue) => WorkGenCreated(queue)
          case Failure(e) => throw e
        }
      case _ =>
    }
    collecting(processedWork, mapping, dists)
  }
}
