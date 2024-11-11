package de.hpi.fgis.dendrotime.actors.coordinator

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props, Terminated}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.{StrategyFactory, StrategyProtocol}
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.actors.{Communicator, TimeSeriesManager}
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.Status

import scala.concurrent.duration.*


object Coordinator {

  type MessageType = Command | StrategyProtocol.StrategyCommand | StrategyProtocol.StrategyEvent
  sealed trait Command
  case object CancelProcessing extends Command
  case object ClusteringFinished extends Command
  case object Stop extends Command

  sealed trait TsLoadingCommand extends Command
  case class DatasetHasNTimeseries(n: Int) extends TsLoadingCommand
  case class NewTimeSeries(tsId: Long) extends TsLoadingCommand
  case class AllTimeSeriesLoaded(ids: Set[Long]) extends TsLoadingCommand
  case class FailedToLoadAllTimeSeries(cause: String) extends TsLoadingCommand

  case class ApproximationResult(t1: Long, t2: Long, t1Idx: Int, t2Idx: Int, dist: Double) extends Command
  case class FullResult(t1: Long, t2: Long, t1Idx: Int, t2Idx: Int, dist: Double) extends Command

  sealed trait Response
  case class ProcessingStarted(id: Long, communicator: ActorRef[Communicator.Command]) extends Response
  case class ProcessingEnded(id: Long) extends Response
  case class ProcessingFailed(id: Long) extends Response
  case class ProcessingStatus(id: Long, status: Status) extends Response

  def apply(
             tsManager: ActorRef[TimeSeriesManager.Command],
             id: Long,
             dataset: Dataset,
             params: DendroTimeParams,
             reportTo: ActorRef[Response]): Behavior[MessageType] = Behaviors.setup { ctx =>
    val settings = Settings(ctx.system)
    val stashSize = settings.numberOfWorkers * 5

    Behaviors.withStash(stashSize) { stash =>
      new Coordinator(ctx, tsManager, id, dataset, params, reportTo, stash).start()
    }
  }

  def props: Props = DispatcherSelector.fromConfig("dendrotime.coordinator-pinned-dispatcher")
}

private class Coordinator private (
                   ctx: ActorContext[Coordinator.MessageType],
                   tsManager: ActorRef[TimeSeriesManager.Command],
                   id: Long,
                   dataset: Dataset,
                   params: DendroTimeParams,
                   reportTo: ActorRef[Coordinator.Response],
                   stash: StashBuffer[Coordinator.MessageType]
                 ) {

  import Coordinator.*

  private val settings = Settings(ctx.system)
  private val communicator = ctx.spawn(Communicator(dataset), s"communicator-$id")
  ctx.watch(communicator)
  private val clusterer = ctx.spawn(
    Clusterer(tsManager, communicator, dataset, params),
    s"clusterer-$id",
    Clusterer.props
  )
  ctx.watch(clusterer)
  private val workers = {
    val router = Routers.pool(settings.numberOfWorkers)(Worker(tsManager, ctx.self, params))
      .withRouteeProps(Worker.props)
      // broadcast supplier reference to all workers
      .withBroadcastPredicate{
        case Worker.UseSupplier(_) => true
        case _ => false
      }
      .withRoundRobinRouting()
    ctx.spawn(router, s"worker-pool-$id")
  }
  private val fullStrategy = ctx.spawn(
    StrategyFactory(params.strategy, StrategyParameters(dataset, params, tsManager, clusterer), ctx.self),
    s"strategy-$id",
    StrategyFactory.props
  )
  ctx.watch(fullStrategy)
  private val approxWorkQueue = WorkQueue.empty[(Long, Long)]


  private def start(): Behavior[MessageType] = {
    tsManager ! TimeSeriesManager.GetTimeSeriesIds(Right(dataset), ctx.self)
    reportTo ! ProcessingStarted(id, communicator)
    workers ! Worker.UseSupplier(ctx.self.narrow[StrategyProtocol.StrategyCommand])

    initializing(Vector.empty)
  }

  private def initializing(tsIds: Seq[Long]): Behavior[MessageType] =
    Behaviors.receiveMessagePartial {
      case NewTimeSeries(tsId) =>
        ctx.log.debug("New time series ts-{} for dataset d-{} was loaded!", tsId, dataset.id)
        fullStrategy ! StrategyProtocol.AddTimeSeries(Seq(tsId))
        if tsIds.isEmpty then
          initializing(tsIds :+ tsId)
        else
          approxWorkQueue.enqueueAll(tsIds.map((_, tsId)))
          stash.unstashAll(
            initializing(tsIds :+ tsId)
          )

      case DatasetHasNTimeseries(n) =>
        ctx.log.info("Dataset d-{} has {} time series, starting clusterer and SWITCHING TO LOADING STATE", dataset.id, n)
        approxWorkQueue.sizeHint(n * (n - 1) / 2)
        // switch to loading state
        clusterer ! Clusterer.Initialize(n)
        stash.unstashAll(loading(n, tsIds))

      case StrategyProtocol.DispatchWork(worker) if approxWorkQueue.hasWork =>
        val work = approxWorkQueue.dequeue()
        ctx.log.trace("Dispatching approx job ({}) ApproxQueue={}", work, approxWorkQueue)
        worker ! Worker.CheckApproximate(work._1, work._2)
        initializing(tsIds)

      case m: StrategyProtocol.DispatchWork =>
        ctx.log.debug("Worker {} asked for work but there is none", m.worker)
        stash.stash(m)
        Behaviors.same

      case ApproximationResult(t1, t2, idx1, idx2, dist) =>
        ctx.log.trace("Approx result {}-{}: {}", t1, t2, dist)
        clusterer ! Clusterer.ApproximateDistance(idx1, idx2, dist)
        approxWorkQueue.removePending((t1, t2))
        initializing(tsIds)

      case FailedToLoadAllTimeSeries(_) =>
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped

      case CancelProcessing =>
        ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped
    }

  private def loading(nTimeseries: Int, tsIds: Seq[Long]):  Behavior[MessageType] =
    Behaviors.receiveMessagePartial {
      case NewTimeSeries(tsId) =>
        ctx.log.debug("New time series ts-{} for dataset d-{} was loaded!", tsId, dataset.id)
        fullStrategy ! StrategyProtocol.AddTimeSeries(Seq(tsId))
        if tsIds.isEmpty then
          loading(nTimeseries, tsIds :+ tsId)
        else
          approxWorkQueue.enqueueAll(tsIds.map((_, tsId)))
          stash.unstashAll(
            loading(nTimeseries, tsIds :+ tsId)
          )

      case AllTimeSeriesLoaded(allTsIds) =>
        if allTsIds.size != tsIds.size || allTsIds.size != nTimeseries then
          throw new IllegalStateException(f"Not all time series were loaded (${tsIds.size} of $nTimeseries)")

        // switch to approximating state
        ctx.log.info("All {} time series loaded for dataset d-{}, SWITCHING TO APPROXIMATING STATE", allTsIds.size, dataset.id)
        communicator ! Communicator.NewStatus(Status.Approximating)
        stash.unstashAll(approximating(allTsIds, nTimeseries * (nTimeseries - 1) / 2))

      case StrategyProtocol.DispatchWork(worker) if approxWorkQueue.hasWork =>
        val work = approxWorkQueue.dequeue()
        ctx.log.trace("Dispatching approx job ({}) ApproxQueue={}", work, approxWorkQueue)
        worker ! Worker.CheckApproximate(work._1, work._2)
        loading(nTimeseries, tsIds)

      case m: StrategyProtocol.DispatchWork =>
        ctx.log.debug("Worker {} asked for work but there is none", m.worker)
        stash.stash(m)
        Behaviors.same

      case ApproximationResult(t1, t2, idx1, idx2, dist) =>
        ctx.log.trace("Approx result {}-{}: {}", t1, t2, dist)
        clusterer ! Clusterer.ApproximateDistance(idx1, idx2, dist)
        approxWorkQueue.removePending((t1, t2))
        loading(nTimeseries, tsIds)

      case FailedToLoadAllTimeSeries(_) =>
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped

      case CancelProcessing =>
        ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped
    }

  private def approximating(allIds: Set[Long], fullPending: Int): Behavior[MessageType] =
    Behaviors.receiveMessagePartial {
      case StrategyProtocol.DispatchWork(worker) if approxWorkQueue.hasWork =>
        val work = approxWorkQueue.dequeue()
        ctx.log.trace("Dispatching approx job ({}) ApproxQueue={}", work, approxWorkQueue)
        worker ! Worker.CheckApproximate(work._1, work._2)
        Behaviors.same

      case StrategyProtocol.DispatchWork(worker) => // if approxWorkQueue.noWork
        ctx.log.debug("Approx queue ran out of work, switching to full strategy")
        fullStrategy ! StrategyProtocol.DispatchWork(worker)
        // switch supplier for worker
        worker ! Worker.UseSupplier(fullStrategy)
        Behaviors.same

      case ApproximationResult(t1, t2, idx1, idx2, dist) =>
        ctx.log.trace("Approx result {}-{}: {}", t1, t2, dist)
        approxWorkQueue.removePending((t1, t2))
        clusterer ! Clusterer.ApproximateDistance(idx1, idx2, dist)
        communicator ! Communicator.ProgressUpdate(Status.Approximating, progress(approxWorkQueue.size, allIds.size))

        if approxWorkQueue.hasPending then
          Behaviors.same
        else
          ctx.log.info("approx={} no more work, SWITCHING TO FULL STATE", approxWorkQueue)
          communicator ! Communicator.NewStatus(Status.ComputingFullDistances)
          communicator ! Communicator.ProgressUpdate(Status.ComputingFullDistances, progress(approxWorkQueue.size, allIds.size))
          computingFullDistances(fullPending, allIds)

      case FullResult(t1, t2, idx1, idx2, dist) =>
        ctx.log.trace("Full result {}-{}: {}", t1, t2, dist)
        clusterer ! Clusterer.FullDistance(idx1, idx2, dist)
        communicator ! Communicator.ProgressUpdate(Status.ComputingFullDistances, progress(fullPending - 1, allIds.size))
        approximating(allIds, fullPending - 1)

      case CancelProcessing =>
        ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped
    }

  private def computingFullDistances(pending: Int, allIds: Set[Long]): Behavior[MessageType] =
    Behaviors.receiveMessagePartial {
      case StrategyProtocol.DispatchWork(worker) =>
        ctx.log.warn("Forwarding misguided work request from {} to full strategy", worker)
        fullStrategy ! StrategyProtocol.DispatchWork(worker)
        // switch supplier for worker
        worker ! Worker.UseSupplier(fullStrategy)
        Behaviors.same

      case FullResult(t1, t2, idx1, idx2, dist) =>
        ctx.log.trace("Full result {}-{}: {}", t1, t2, dist)
        clusterer ! Clusterer.FullDistance(idx1, idx2, dist)
        communicator ! Communicator.ProgressUpdate(Status.ComputingFullDistances, progress(pending, allIds.size))
        computingFullDistances(pending - 1, allIds)

      case StrategyProtocol.FullStrategyOutOfWork =>
        ctx.log.debug("No more work to do, waiting for pending results!")
        communicator ! Communicator.NewStatus(Status.Finalizing)
        communicator ! Communicator.ProgressUpdate(Status.Finalizing, 50)
        Behaviors.same

      case StrategyProtocol.FullStrategyFinished =>
        if pending != 0 then
          throw new IllegalStateException(f"Full strategy finished but still $pending pending jobs")
        ctx.log.info("Full strategy finished, SWITCHING TO WAITING_FOR_CLUSTERING STATE!")
        clusterer ! Clusterer.ReportFinished(ctx.self)
        ctx.unwatch(clusterer)
        waitingForClustering(Cancellable.alreadyCancelled)

      case CancelProcessing =>
        ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped
    }

  private def waitingForClustering(stopTimer: Cancellable): Behavior[MessageType] = Behaviors.receiveMessagePartial[MessageType] {
    case ClusteringFinished =>
      communicator ! Communicator.ProgressUpdate(Status.Finalizing, 100)
      communicator ! Communicator.NewStatus(Status.Finished)
      reportTo ! ProcessingStatus(id, Status.Finished)
      ctx.log.info("FINISHED processing job {}", id)
      val stopTimer = ctx.scheduleOnce(30 seconds span, ctx.self, Stop)
      waitingForClustering(stopTimer)

    case Stop =>
      // do not directly stop coordinator to let communicator store the final state to disk (if enabled)
      ctx.log.debug("Shutting down communicator")
      ctx.stop(communicator)
      stopTimer.cancel()
      Behaviors.same

    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      stopTimer.cancel()
      Behaviors.stopped

  }.receiveSignal{
    case (_, Terminated(`communicator`)) =>
      ctx.log.debug("Communicator is finished, shutting down coordinator!")
      reportTo ! ProcessingEnded(id)
      Behaviors.stopped
  }

  private def progress(pending: Int, allIds: Int): Int = {
    val nJobs = (allIds * (allIds - 1)) / 2
    100 - (pending.toDouble / nJobs * 100).toInt
  }
}
