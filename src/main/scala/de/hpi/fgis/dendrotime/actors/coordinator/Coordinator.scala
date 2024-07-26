package de.hpi.fgis.dendrotime.actors.coordinator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, SupervisorStrategy}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.{Clusterer, Communicator, TimeSeriesManager}
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.StateModel.Status


object Coordinator {

  sealed trait Command
  case object CancelProcessing extends Command

  sealed trait TsLoadingCommand extends Command
  case class NewTimeSeries(datasetId: Int, tsId: Long) extends TsLoadingCommand
  case class AllTimeSeriesLoaded(datasetId: Int, ids: Set[Long]) extends TsLoadingCommand
  case class FailedToLoadAllTimeSeries(datasetId: Int, cause: String) extends TsLoadingCommand

  case class DispatchWork(worker: ActorRef[Worker.Command]) extends Command
  case class ApproximationResult(t1: Long, t2: Long, dist: Double) extends Command

  sealed trait Response
  case class ProcessingStarted(id: Long, communicator: ActorRef[Communicator.Command]) extends Response
  case class ProcessingEnded(id: Long) extends Response
  case class ProcessingFailed(id: Long) extends Response
  case class ProcessingStatus(id: Long, status: Status) extends Response

  def apply(
             tsManager: ActorRef[TimeSeriesManager.Command],
             id: Long,
             dataset: Dataset,
             reportTo: ActorRef[Response]): Behavior[Command] = Behaviors.setup { ctx =>
    val settings = Settings(ctx.system)
    val stashSize = settings.numberOfWorkers * 5

    Behaviors.withStash(stashSize) { stash =>
      new Coordinator(ctx, tsManager, id, dataset, reportTo, stash).start()
    }
  }
}

private class Coordinator private (
                   ctx: ActorContext[Coordinator.Command],
                   tsManager: ActorRef[TimeSeriesManager.Command],
                   id: Long,
                   dataset: Dataset,
                   reportTo: ActorRef[Coordinator.Response],
                   stash: StashBuffer[Coordinator.Command]
                 ) {

  import Coordinator.*

  private val settings = Settings(ctx.system)
  private val communicator = ctx.spawn(Communicator(), s"communicator-$id")
  private val clusterer = ctx.spawn(Clusterer(communicator), s"clusterer-$id")
  private val workers = {
    val supervisedWorkerBehavior = Behaviors
      .supervise(Worker(tsManager, ctx.self, communicator, dataset.id))
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

    case AllTimeSeriesLoaded(_, allTsIds) =>
      ctx.log.info("All {} time series loaded for dataset d-{}", allTsIds.size, dataset.id)
      if allTsIds.size != tsIds.size then
        throw new IllegalStateException("Not all time series were loaded")

      // switch to approximating state
      clusterer ! Clusterer.Initialize(allTsIds.size)
      communicator ! Communicator.NewStatus(Status.Approximating)
      stash.unstashAll(approximating(workQueue, allTsIds))

    case FailedToLoadAllTimeSeries(_, reason) =>
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

    case DispatchWork(worker) if workQueue.hasWork =>
      val (work, newQueue) = workQueue.dequeue()
      ctx.log.debug("Dispatching job ({}) to worker {}", work, worker)
      worker ! Worker.CheckApproximate(work._1, work._2)
      initializing(tsIds, newQueue)

    case m: DispatchWork =>
      ctx.log.debug("Worker {} asked for work but there is none", m.worker)
      stash.stash(m)
      Behaviors.same

    case ApproximationResult(t1, t2, dist) =>
      ctx.log.info("Approximation result for ts-{} and ts-{}: {}", t1, t2, dist)
      clusterer ! Clusterer.ApproximateDistance(t1.toInt, t2.toInt, dist)
      val newQueue = workQueue.removePending((t1, t2))
      initializing(tsIds, newQueue)
  }

  private def approximating(workQueue: WorkQueue[(Long, Long)], allIds: Set[Long]): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case CancelProcessing =>
        ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped

      case DispatchWork(worker) if workQueue.hasWork =>
        val (work, newQueue) = workQueue.dequeue()
        ctx.log.debug("Dispatching job ({}) to worker {}", work, worker)
        worker ! Worker.CheckApproximate(work._1, work._2)
        approximating(newQueue, allIds)

      case m: DispatchWork =>
        stash.stash(m)
        if workQueue.isEmpty then
          ctx.log.info("No more work to do, temporarily finished!")
          reportTo ! ProcessingEnded(id)
          Behaviors.stopped
        else
          Behaviors.same

      case ApproximationResult(t1, t2, dist) =>
        ctx.log.info("Approximation result for ts-{} and ts-{}: {}", t1, t2, dist)
        val newQueue = workQueue.removePending((t1, t2))
        clusterer ! Clusterer.ApproximateDistance(t1.toInt, t2.toInt, dist)
        communicator ! Communicator.ProgressUpdate(progress(workQueue.size, allIds.size))
        approximating(newQueue, allIds)
    }

  private def computingFullDistances(workQueue: WorkQueue[(Long, Long)], allIds: Set[Long]): Behavior[Command] = Behaviors.receiveMessagePartial {
    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

//    case DispatchWork(worker) if workQueue.hasWork =>
//      val (work, newQueue) = workQueue.dequeue()
//      ctx.log.debug("Dispatching job ({}) to worker {}", work, worker)
//      worker ! Worker.CheckFull(work._1, work._2)
//      computingFullDistances(newQueue)

    case m: DispatchWork =>
      stash.stash(m)
      if workQueue.isEmpty then
        ctx.log.info("No more work to do, temporarily finished!")
        reportTo ! ProcessingEnded(id)
        Behaviors.stopped
      else
        Behaviors.same

    case ApproximationResult(t1, t2, dist) =>
      ctx.log.info("Approximation result for ts-{} and ts-{}: {}", t1, t2, dist)
      val newQueue = workQueue.removePending((t1, t2))
      clusterer ! Clusterer.ApproximateDistance(t1.toInt, t2.toInt, dist)
      communicator ! Communicator.ProgressUpdate(progress(workQueue.size, allIds.size))
      computingFullDistances(newQueue, allIds)
  }

  private def progress(pending: Int, allIds: Int): Int = {
    val nJobs = (allIds * (allIds - 1)) / 2
    100 - (pending.toDouble / nJobs * 100).toInt
  }
}
