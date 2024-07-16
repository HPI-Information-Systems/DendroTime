package de.hpi.fgis.dendrotime.actors.coordinator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.TimeSeriesManager
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset


object Coordinator {

  sealed trait Command
  case class GetStatus(replyTo: ActorRef[ProcessingStatus]) extends Command
  case object CancelProcessing extends Command
  case class NewTimeSeries(datasetId: Int, tsId: Long) extends Command
  case class DispatchWork(worker: ActorRef[Worker.Command]) extends Command
  private case class GetTimeSeriesIdsResponse(msg: TimeSeriesManager.GetTimeSeriesIdsResponse) extends Command

  sealed trait Response
  case class ProcessingEnded(id: Long) extends Response
  case class ProcessingFailed(id: Long) extends Response
  case class ProcessingStatus(id: Long, status: State) extends Response

  sealed trait State
  case object Initializing extends State
  case object Approximating extends State
  case object ComputingFullDistances extends State
  case object Finalizing extends State

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

  private def start(): Behavior[Command] = {
    tsManager ! TimeSeriesManager.GetTimeSeriesIds(Right(dataset), ctx.messageAdapter(GetTimeSeriesIdsResponse.apply))

    initializing(Seq.empty, WorkQueue.empty)
  }

  private def initializing(tsIds: Seq[Long], workQueue: WorkQueue[(Long, Long)]): Behavior[Command] = Behaviors.receiveMessage {
    case GetStatus(replyTo) =>
      replyTo ! ProcessingStatus(id, Initializing)
      Behaviors.same
    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped
    case NewTimeSeries(datasetId, tsId) =>
      ctx.log.info("New time series ts-{} for dataset d-{} was loaded!", tsId, datasetId)
//      if !tsIds.empty then
        // TODO: send out approximation messages early
      initializing(tsIds :+ tsId, workQueue.enqueueAll(tsIds.map((_, tsId))))

    case GetTimeSeriesIdsResponse(TimeSeriesManager.TimeSeriesIdsFound(_, tsIds)) =>
      ctx.log.info("All {} time series loaded for dataset d-{}", tsIds.size, dataset.id)
      // switch to approximating state
      stash.unstashAll(approximating(workQueue))
    case GetTimeSeriesIdsResponse(TimeSeriesManager.FailedToLoadTimeSeriesIds(_, reason)) =>
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

    case m =>
      stash.stash(m)
      Behaviors.same
  }

  private def approximating(workQueue: WorkQueue[(Long, Long)]): Behavior[Command] = Behaviors.receiveMessagePartial {
    case GetStatus(replyTo) =>
      replyTo ! ProcessingStatus(id, Approximating)
      Behaviors.same
    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped
    case DispatchWork(replyTo) =>
      val (work, newQueue) = workQueue.dequeue()
      replyTo ! Worker.CheckApproximate(work._1, work._2)
      approximating(newQueue)
  }
}
