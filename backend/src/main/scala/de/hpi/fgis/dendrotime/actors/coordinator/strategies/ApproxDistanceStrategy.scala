package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, PostStop}
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol.{DistanceMatrix, RegisterApproxDistMatrixReceiver}
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyParameters.InternalStrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.structures.strategies.ApproxDistanceWorkGenerator
import de.hpi.fgis.dendrotime.structures.strategies.ApproxDistanceWorkGenerator.Direction

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object ApproxDistanceStrategy {

  private case class ApproxDistances(dists: PDist) extends StrategyCommand
  private case class WorkGenCreated(queue: ApproxDistanceWorkGenerator) extends StrategyCommand

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

  private val approxDistancesAdapter = ctx.messageAdapter[DistanceMatrix](m => ApproxDistances(m.distances))
  // Executor for internal futures (CPU-heavy work)
  private given ExecutionContext = ctx.system.dispatchers.lookup(DispatcherSelector.blocking())

  override def start(): Behavior[StrategyCommand] = {
    if fallbackWorkGenerator.hasNext then
      params.clusterer ! RegisterApproxDistMatrixReceiver(approxDistancesAdapter)
      collecting()
    else
      Behaviors.stopped
  }

  private def collecting(): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(_) => Behaviors.same // ignore

    case m: DispatchWork => dispatchFallbackWork(m)

    case ApproxDistances(dists) =>
      ctx.log.debug(s"Received approximate distances", dists.n)
      buildQueue(dists)
      Behaviors.same

    case WorkGenCreated(workGen) =>
      if workGen.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      ctx.log.info("Received work queue of size {} ({} already processed), serving", workGen.sizeTuples, processed.size)
      stash.unstashAll(serving(workGen, Some(prepareNextBatch(workGen, nextBatchSize()))))

    case ReportStatus =>
      ctx.log.info("[REPORT] Preparing, {} fallback work items already processed", processed.size)
      Behaviors.same
  }

  private def serving(workGen: ApproxDistanceWorkGenerator, cachedWork: Option[WorkerProtocol.CheckCommand] = None): Behavior[StrategyCommand] = Behaviors.receiveMessagePartial[StrategyCommand] {
    case AddTimeSeries(_) =>
      // ignore
      Behaviors.same

    case DispatchWork(worker, time, size) if cachedWork.isDefined && workGen.hasNext =>
      val m = cachedWork.get
      ctx.log.trace("Dispatching full job ({}) remaining={}, Stash={}", m.length, workGen.remaining, stash.size)
      worker ! m

      // prepare next batch
      val batchSize = nextBatchSize(time, size)
      val work = prepareNextBatch(workGen, batchSize)
      serving(workGen, Some(work))

    case DispatchWork(worker, _, _) if cachedWork.isDefined =>
      val work = cachedWork.get
      ctx.log.trace("Dispatching full job ({}) remaining={}, Stash={}", work.length, workGen.remaining, stash.size)
      worker ! work
      serving(workGen, None)

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

  private def buildQueue(dists: PDist): Unit = {
    val size = dists.n * (dists.n - 1) / 2
    ctx.log.info("Received both approximate distances and mapping, building work Queue of size {} ({} already processed)", size, processed.size)
    val f = Future { ApproxDistanceWorkGenerator(dists, direction) }
    ctx.pipeToSelf(f) {
      case Success(queue) => WorkGenCreated(queue)
      case Failure(e) => throw e
    }
  }

  private def prepareNextBatch(workGen: ApproxDistanceWorkGenerator, batchSize: Int): WorkerProtocol.CheckCommand = {
    val work = workGen.nextBatch(batchSize, processed.contains)
    WorkerProtocol.CheckFull(work)
  }
}
