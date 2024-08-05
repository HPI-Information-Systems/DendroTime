package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.StateModel.ProgressMessage

object Scheduler {

  sealed trait Command
  case class StartProcessing(dataset: Dataset, replyTo: ActorRef[Response]) extends Command
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
    case StartProcessing(d, replyTo) =>
      ctx.log.info("Start processing dataset {}", d)
      val newJobId = jobId + 1
      replyTo ! ProcessingStarted(newJobId)
      val coordinator = startNewJob(newJobId, d, replyTo)
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
  }

  private def starting(jobId: Long, dataset: Dataset, coordinator: ActorRef[Coordinator.Command]): Behavior[Command] = {
    val behavior: PartialFunction[Command, Behavior[Command]] = {
      case ProcessingResponse(Coordinator.ProcessingStarted(id, communicator), replyTo) =>
        ctx.log.info("Successfully started processing dataset job={}", id)
        replyTo ! ProcessingStarted(id)
        inProgress(id, dataset, coordinator, communicator)
      case GetProgress(id, replyTo) =>
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
      case GetProgress(id, replyTo) =>
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
    case StartProcessing(d, replyTo) =>
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

    case ProcessingResponse(Coordinator.ProcessingEnded(id), replyTo) =>
      ctx.log.info("Successfully processed dataset job={}", id)
      //        replyTo ! ProcessingFinished(d)
      ctx.unwatch(coordinator)
      idle(jobId)
    case ProcessingResponse(Coordinator.ProcessingFailed(id), replyTo) =>
      ctx.log.error("Failed to process dataset job={}", id)
      //        replyTo ! ProcessingFailed(d)
      Behaviors.same
    case ProcessingResponse(m @ Coordinator.ProcessingStatus(_, _), _) =>
      ctx.log.debug("Ignored status message: {}", m)
      // ignore
      Behaviors.same
  }
  
  private def startNewJob(id: Long, dataset: Dataset, replyTo: ActorRef[ProcessingOutcome]): ActorRef[Coordinator.Command] = {
    val msgAdapter = ctx.messageAdapter(ProcessingResponse(_, replyTo))
    val coordinator = ctx.spawn(Coordinator(tsManager, id, dataset, msgAdapter), s"coordinator-$id")
    ctx.watch(coordinator)
    coordinator
  }
}