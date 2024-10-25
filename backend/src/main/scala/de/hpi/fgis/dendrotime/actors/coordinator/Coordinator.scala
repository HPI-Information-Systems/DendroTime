package de.hpi.fgis.dendrotime.actors.coordinator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, SupervisorStrategy}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer
import de.hpi.fgis.dendrotime.actors.{Communicator, TimeSeriesManager}
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.Status

import scala.concurrent.duration.*


object Coordinator {

  sealed trait Command
  case object CancelProcessing extends Command
  case object ClusteringFinished extends Command
  case object Stop extends Command

  sealed trait TsLoadingCommand extends Command
  case class DatasetHasNTimeseries(n: Int) extends TsLoadingCommand
  case class NewTimeSeries(datasetId: Int, tsId: Long) extends TsLoadingCommand
  case class AllTimeSeriesLoaded(datasetId: Int, ids: Set[Long]) extends TsLoadingCommand
  case class FailedToLoadAllTimeSeries(datasetId: Int, cause: String) extends TsLoadingCommand

  case class DispatchWork(worker: ActorRef[Worker.Command]) extends Command
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
             reportTo: ActorRef[Response]): Behavior[Command] = Behaviors.setup { ctx =>
    val settings = Settings(ctx.system)
    val stashSize = settings.numberOfWorkers * 5

    Behaviors.withStash(stashSize) { stash =>
      new Coordinator(ctx, tsManager, id, dataset, params, reportTo, stash).start()
    }
  }
}

private class Coordinator private (
                   ctx: ActorContext[Coordinator.Command],
                   tsManager: ActorRef[TimeSeriesManager.Command],
                   id: Long,
                   dataset: Dataset,
                   params: DendroTimeParams,
                   reportTo: ActorRef[Coordinator.Response],
                   stash: StashBuffer[Coordinator.Command]
                 ) {

  import Coordinator.*

  private val settings = Settings(ctx.system)
  private val communicator = ctx.spawn(Communicator(), s"communicator-$id")
  ctx.watch(communicator)
  private val clusterer = ctx.spawn(Clusterer(communicator, dataset, params), s"clusterer-$id")
  ctx.watch(clusterer)
  private val workers = {
    val supervisedWorkerBehavior = Behaviors
      .supervise(Worker(tsManager, ctx.self, communicator, params))
      .onFailure[Exception](SupervisorStrategy.restart)
    val router = Routers.pool(settings.numberOfWorkers)(supervisedWorkerBehavior)
      .withRouteeProps(DispatcherSelector.blocking())
      .withRoundRobinRouting()
    ctx.spawn(router, s"worker-pool-$id")
  }


  private def start(): Behavior[Command] = {
    tsManager ! TimeSeriesManager.GetTimeSeriesIds(Right(dataset), ctx.self)
    reportTo ! ProcessingStarted(id, communicator)

    initializing(Seq.empty, WorkQueue.empty)
  }

  private def initializing(tsIds: Seq[Long], workQueue: WorkQueue[(Long, Long)]): Behavior[Command] = Behaviors.receiveMessage {
    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

    case NewTimeSeries(datasetId, tsId) =>
      ctx.log.info("New time series ts-{} for dataset d-{} was loaded!", tsId, datasetId)
      if tsIds.isEmpty then
        initializing(tsIds :+ tsId, workQueue)
      else
        stash.unstashAll(
          initializing(tsIds :+ tsId, workQueue.enqueueAll(tsIds.map((_, tsId))))
        )

    case DatasetHasNTimeseries(n) =>
      ctx.log.info("Dataset d-{} has {} time series, starting clusterer and switching to loading state", dataset.id, n)
      // switch to loading state
      clusterer ! Clusterer.Initialize(n)
      stash.unstashAll(loading(n, tsIds, workQueue))

    case FailedToLoadAllTimeSeries(_, _) =>
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

    case DispatchWork(worker) if workQueue.hasWork =>
      val (work, newQueue) = workQueue.dequeue()
      ctx.log.debug("Dispatching approx job ({}) ApproxQueue={}", work, newQueue)
      worker ! Worker.CheckApproximate(work._1, work._2)
      initializing(tsIds, newQueue)

    case m: DispatchWork =>
      ctx.log.debug("Worker {} asked for work but there is none", m.worker)
      stash.stash(m)
      Behaviors.same

    case ApproximationResult(t1, t2, idx1, idx2, dist) =>
      ctx.log.debug("Approx result {}-{}: {}", t1, t2, dist)
      clusterer ! Clusterer.ApproximateDistance(idx1, idx2, dist)
      val newQueue = workQueue.removePending((t1, t2))
      initializing(tsIds, newQueue)

    case m =>
      throw new IllegalStateException(f"STATE=initializing: Received unexpected message $m!")
  }

  private def loading(nTimeseries: Int, tsIds: Seq[Long], workQueue: WorkQueue[(Long, Long)]):  Behavior[Command] = Behaviors.receiveMessage {
    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

    case NewTimeSeries(datasetId, tsId) =>
      ctx.log.info("New time series ts-{} for dataset d-{} was loaded!", tsId, datasetId)
      if tsIds.isEmpty then
        loading(nTimeseries, tsIds :+ tsId, workQueue)
      else
        stash.unstashAll(
          loading(nTimeseries, tsIds :+ tsId, workQueue.enqueueAll(tsIds.map((_, tsId))))
        )

    case AllTimeSeriesLoaded(_, allTsIds) =>
      ctx.log.info("All {} time series loaded for dataset d-{}", allTsIds.size, dataset.id)
      if allTsIds.size != tsIds.size || allTsIds.size != nTimeseries then
        throw new IllegalStateException(f"Not all time series were loaded (${tsIds.size} of $nTimeseries)")

      // switch to approximating state
      communicator ! Communicator.NewStatus(Status.Approximating)
      val fullQueue = WorkQueue.from(
        tsIds.combinations(2).map(p => (p(0), p(1))).to(Iterable)
      )
      stash.unstashAll(approximating(workQueue, fullQueue, allTsIds))

    case FailedToLoadAllTimeSeries(_, _) =>
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

    case DispatchWork(worker) if workQueue.hasWork =>
      val (work, newQueue) = workQueue.dequeue()
      ctx.log.debug("Dispatching approx job ({}) ApproxQueue={}", work, newQueue)
      worker ! Worker.CheckApproximate(work._1, work._2)
      loading(nTimeseries, tsIds, newQueue)

    case m: DispatchWork =>
      ctx.log.debug("Worker {} asked for work but there is none", m.worker)
      stash.stash(m)
      Behaviors.same

    case ApproximationResult(t1, t2, idx1, idx2, dist) =>
      ctx.log.debug("Approx result {}-{}: {}", t1, t2, dist)
      clusterer ! Clusterer.ApproximateDistance(idx1, idx2, dist)
      val newQueue = workQueue.removePending((t1, t2))
      loading(nTimeseries, tsIds, newQueue)

    case m =>
      throw new IllegalStateException(f"STATE:loading: Received unexpected message $m!")
  }

  private def approximating(approxWorkQueue: WorkQueue[(Long, Long)], fullWorkQueue: WorkQueue[(Long, Long)], allIds: Set[Long]): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case CancelProcessing =>
        ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped

      case DispatchWork(worker) if approxWorkQueue.hasWork =>
        val (work, newQueue) = approxWorkQueue.dequeue()
        ctx.log.debug("Dispatching approx job ({}) ApproxQueue={}", work, newQueue)
        worker ! Worker.CheckApproximate(work._1, work._2)
        approximating(newQueue, fullWorkQueue, allIds)

      case DispatchWork(worker) => // if approxWorkQueue.noWork && fullWorkQueue.hasWork
        val (work, newQueue) = fullWorkQueue.dequeue()
        ctx.log.debug("Dispatching full job ({}) ApproxQueue={}, FullQueue={}", work, approxWorkQueue, newQueue)
        worker ! Worker.CheckFull(work._1, work._2)
        if approxWorkQueue.hasPending then
          approximating(approxWorkQueue, newQueue, allIds)
        else
          ctx.log.info("Changing to full: approx={}, full={}", approxWorkQueue, newQueue)
          communicator ! Communicator.NewStatus(Status.ComputingFullDistances)
          communicator ! Communicator.ProgressUpdate(Status.ComputingFullDistances, progress(approxWorkQueue.size, allIds.size))
          computingFullDistances(newQueue, allIds)

      case ApproximationResult(t1, t2, idx1, idx2, dist) =>
        ctx.log.debug("Approx result {}-{}: {}", t1, t2, dist)
        val newQueue = approxWorkQueue.removePending((t1, t2))
        clusterer ! Clusterer.ApproximateDistance(idx1, idx2, dist)
        communicator ! Communicator.ProgressUpdate(Status.Approximating, progress(newQueue.size, allIds.size))
        approximating(newQueue, fullWorkQueue, allIds)

      case FullResult(t1, t2, idx1, idx2, dist) =>
        ctx.log.debug("Full result {}-{}: {}", t1, t2, dist)
        val newQueue = fullWorkQueue.removePending((t1, t2))
        clusterer ! Clusterer.FullDistance(idx1, idx2, dist)
        communicator ! Communicator.ProgressUpdate(Status.ComputingFullDistances, progress(newQueue.size, allIds.size))
        approximating(approxWorkQueue, newQueue, allIds)
    }

  private def computingFullDistances(workQueue: WorkQueue[(Long, Long)], allIds: Set[Long]): Behavior[Command] = Behaviors.receiveMessagePartial {
    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

    case DispatchWork(worker) if workQueue.hasWork =>
      val (work, newQueue) = workQueue.dequeue()
      ctx.log.debug("Dispatching full job ({}) FullQueue={}", work, newQueue)
      worker ! Worker.CheckFull(work._1, work._2)
      computingFullDistances(newQueue, allIds)

    case m: DispatchWork =>
      stash.stash(m)
      if workQueue.isEmpty then
        ctx.log.info("No more work to do, waiting for clustering!")
        communicator ! Communicator.NewStatus(Status.Finalizing)
        communicator ! Communicator.ProgressUpdate(Status.Finalizing, 50)
        if workQueue.hasPending then
          Behaviors.same
        else
          clusterer ! Clusterer.ReportFinished(ctx.self)
          ctx.unwatch(clusterer)
          reportTo ! ProcessingStatus(id, Status.Finished)
          Behaviors.withTimers { timers =>
            timers.startSingleTimer("stopping-timeout", Stop, 30 seconds span)
            waitingForClustering()
          }
      else
        Behaviors.same

    case FullResult(t1, t2, idx1, idx2, dist) =>
      ctx.log.debug("Full result {}-{}: {}", t1, t2, dist)
      val newQueue = workQueue.removePending((t1, t2))
      clusterer ! Clusterer.FullDistance(idx1, idx2, dist)
      communicator ! Communicator.ProgressUpdate(Status.ComputingFullDistances, progress(newQueue.size, allIds.size))
      computingFullDistances(newQueue, allIds)
  }

  private def waitingForClustering(): Behavior[Command] = Behaviors.receiveMessage {
    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

    case ClusteringFinished =>
      communicator ! Communicator.ProgressUpdate(Status.Finalizing, 100)
      communicator ! Communicator.NewStatus(Status.Finished)
      ctx.log.info("Finished processing job {}", id)
      Behaviors.same

    case Stop =>
      ctx.log.info("Shutting down coordinator")
      reportTo ! ProcessingEnded(id)
      Behaviors.stopped

    case m =>
      ctx.log.warn("Received unexpected message! {}", m)
      Behaviors.same
  }

  private def progress(pending: Int, allIds: Int): Int = {
    val nJobs = (allIds * (allIds - 1)) / 2
    100 - (pending.toDouble / nJobs * 100).toInt
  }
}
