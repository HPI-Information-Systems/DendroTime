package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator.Stop
import de.hpi.fgis.dendrotime.actors.tsmanager.TimeSeriesManager
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.ProgressMessage
import de.hpi.fgis.dendrotime.structures.Status

import scala.util.{Failure, Success, Try}

object Scheduler {

  sealed trait Command
  case class StartProcessing(dataset: Dataset, params: DendroTimeParams, replyTo: ActorRef[Response]) extends Command
  case class StopProcessing(id: Long, replyTo: ActorRef[Try[Unit]]) extends Command
  case class GetStatus(replyTo: ActorRef[ProcessingStatus]) extends Command
  case class CancelProcessing(id: Long, replyTo: ActorRef[ProcessingCancelled]) extends Command
  private case class ProcessingResponse(msg: Coordinator.Response, replyTo: ActorRef[ProcessingOutcome]) extends Command
  case class GetProgress(id: Long, replyTo: ActorRef[ProgressMessage]) extends Command

  sealed trait Response
  final case class ProcessingStatus(id: Long, dataset: Option[Dataset])
  final case class ProcessingCancelled(id: Long, cause: String)

  sealed trait ProcessingOutcome extends Response
  final case class ProcessingStarted(id: Long) extends ProcessingOutcome
  case object ProcessingRejected extends ProcessingOutcome
  final case class ProcessingFinished(id: Long) extends ProcessingOutcome
  final case class ProcessingFailed(id: Long) extends ProcessingOutcome

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    new Scheduler(ctx).start()
  }
}

private class Scheduler private(ctx: ActorContext[Scheduler.Command]) {
  import Scheduler.*

  private val tsManager = ctx.spawn(TimeSeriesManager(), "time-series-manager")
  ctx.watch(tsManager)

  private def start(): Behavior[Command] = idle(0)

  private def idle(jobId: Long): Behavior[Command] = Behaviors.receiveMessagePartial[Command] {
    case StartProcessing(d, params, replyTo) =>
      ctx.log.info("Start processing dataset {}", d)
      val newJobId = jobId + 1
      val coordinator = startNewJob(newJobId, d, params, replyTo)
      starting(newJobId, d, coordinator)

    case GetStatus(replyTo) =>
      replyTo ! ProcessingStatus(jobId, None)
      Behaviors.same

    case GetProgress(id, replyTo) =>
      replyTo ! ProgressMessage.Unchanged
      Behaviors.same

    case CancelProcessing(id, replyTo) =>
      ctx.log.warn("No dataset is currently being processed!")
      replyTo ! ProcessingCancelled(id, "Nothing changed, no dataset is currently being processed!")
      Behaviors.same

    case StopProcessing(_, replyTo) =>
      replyTo ! Failure[Unit](new IllegalStateException("No dataset is currently being processed!"))
      Behaviors.same
  }

  private def starting(jobId: Long, dataset: Dataset, coordinator: ActorRef[Coordinator.Command]): Behavior[Command] = {
    val behavior: PartialFunction[Command, Behavior[Command]] = {
      case ProcessingResponse(Coordinator.ProcessingStarted(id, communicator), replyTo) =>
        ctx.log.info("Successfully started processing dataset job={}", id)
        replyTo ! ProcessingStarted(id)
        inProgress(id, dataset, coordinator, communicator)

      case ProcessingResponse(Coordinator.ProcessingEnded(id), _) =>
        ctx.log.info("Successfully processed dataset job={}", id)
        ctx.unwatch(coordinator)
        idle(jobId)

      case GetProgress(_, replyTo) =>
        replyTo ! ProgressMessage.Unchanged
        Behaviors.same
    }
    Behaviors.receiveMessage[Command](behavior orElse commonProcessing(jobId, dataset, coordinator)).receiveSignal {
      case (_, Terminated(actorRef)) =>
        ctx.log.error("Coordinator {} terminated, failed to process dataset {}", actorRef, jobId)
        idle(jobId)
    }
  }

  private def inProgress(jobId: Long, dataset: Dataset,
                         coordinator: ActorRef[Coordinator.Command],
                         communicator: ActorRef[Communicator.Command]): Behavior[Command] = {
    val behavior: PartialFunction[Command, Behavior[Command]] = {
      case ProcessingResponse(Coordinator.ProcessingStarted(_, _), _) =>
        ctx.log.warn("Received duplicated processing started message, ignoring")
        Behaviors.same

      case ProcessingResponse(Coordinator.ProcessingStatus(`jobId`, Status.Finished), _) =>
        ctx.log.debug("Processing finished, waiting for stop message")
        ctx.unwatch(coordinator)
        finished(jobId, dataset, coordinator, communicator)

      case GetProgress(_, replyTo) =>
        communicator ! Communicator.GetProgress(replyTo)
        Behaviors.same
    }
    Behaviors.receiveMessage[Command](behavior orElse commonProcessing(jobId, dataset, coordinator)).receiveSignal {
      case (_, Terminated(actorRef)) =>
        ctx.log.error("Coordinator {} terminated, failed to process dataset {}", actorRef, jobId)
        idle(jobId)
    }
  }

  private def commonProcessing(jobId: Long, dataset: Dataset,
                               coordinator: ActorRef[Coordinator.Command]): PartialFunction[Command, Behavior[Command]] = {
    case StartProcessing(d, _, replyTo) =>
      ctx.log.warn("Already processing a dataset, ignoring request to start processing dataset {}", d)
      replyTo ! ProcessingRejected
      Behaviors.same

    case GetStatus(replyTo) =>
      replyTo ! ProcessingStatus(jobId, Some(dataset))
      Behaviors.same

    case CancelProcessing(id, replyTo) =>
      if id == jobId then
        ctx.log.info("Cancelling processing job with ID {} (dataset: {})", id, dataset)
        coordinator ! Coordinator.CancelProcessing
        replyTo ! ProcessingCancelled(jobId, "Cancelled by user")
        Behaviors.same
      else
        ctx.log.warn("No dataset with id {} is currently being processed", id)
        replyTo ! ProcessingCancelled(id, s"The job with id $id is currently not being processed (current=$jobId)")
        Behaviors.same

    case ProcessingResponse(Coordinator.ProcessingEnded(id), _) =>
      ctx.log.info("Successfully processed dataset job={}", id)
      ctx.unwatch(coordinator)
      coordinator ! Stop
      idle(jobId)

    case ProcessingResponse(Coordinator.ProcessingFailed(id), _) =>
      ctx.log.error("Failed to process dataset job={}", id)
      Behaviors.same

    case ProcessingResponse(Coordinator.ProcessingStatus(_, _), _) =>
      // ignore message
      Behaviors.same

    case StopProcessing(_, replyTo) =>
      replyTo ! Failure[Unit](new IllegalStateException("Processing is still underway! Use the cancel command instead."))
      Behaviors.same
  }

  private def finished(jobId: Long, dataset: Dataset,
                       coordinator: ActorRef[Coordinator.Command],
                       communicator: ActorRef[Communicator.Command]): Behavior[Command] = Behaviors.receiveMessage{
    case StartProcessing(d, _, replyTo) =>
      ctx.log.warn("Already processing a dataset, ignoring request to start processing dataset {}", d)
      replyTo ! ProcessingRejected
      Behaviors.same

    case GetStatus(replyTo) =>
      replyTo ! ProcessingStatus(jobId, Some(dataset))
      Behaviors.same

    case GetProgress(_, replyTo) =>
      communicator ! Communicator.GetProgress(replyTo)
      Behaviors.same

    case CancelProcessing(id, replyTo) =>
      if id == jobId then
        ctx.log.info("Cancelling processing job with ID {} (dataset: {})", id, dataset)
        coordinator ! Coordinator.CancelProcessing
        replyTo ! ProcessingCancelled(jobId, "Cancelled by user")
        Behaviors.same
      else
        ctx.log.warn("No dataset with id {} is currently being processed", id)
        replyTo ! ProcessingCancelled(id, s"The job with id $id is currently not being processed (current=$jobId)")
        Behaviors.same

    case ProcessingResponse(Coordinator.ProcessingStarted(_, _), _) =>
      ctx.log.warn("Received duplicated processing started message, ignoring")
      Behaviors.same

    case ProcessingResponse(Coordinator.ProcessingEnded(id), _) =>
      ctx.log.info("Successfully processed dataset job={}", id)
      idle(jobId)

    case ProcessingResponse(Coordinator.ProcessingFailed(id), _) =>
      ctx.log.error("Failed to process dataset job={}", id)
      Behaviors.same

    case ProcessingResponse(Coordinator.ProcessingStatus(_, _), _) =>
      // ignore
      Behaviors.same

    case StopProcessing(_, replyTo) =>
      coordinator ! Stop
      replyTo ! Success[Unit](())
      Behaviors.same
  }
  
  private def startNewJob(id: Long, dataset: Dataset, params: DendroTimeParams, replyTo: ActorRef[ProcessingOutcome]): ActorRef[Coordinator.Command] = {
    val msgAdapter = ctx.messageAdapter(ProcessingResponse(_, replyTo))
    val coordinator = ctx.spawn(
      Coordinator(tsManager, id, dataset, params, msgAdapter),
      s"coordinator-$id",
      Coordinator.props
    )
    ctx.watch(coordinator)
    coordinator
  }
}
