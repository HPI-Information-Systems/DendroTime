package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.{ActorRef, Behavior, Terminated}
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset

import scala.concurrent.duration.given

object Scheduler {

  sealed trait Command
  case class StartProcessing(dataset: Dataset, replyTo: ActorRef[Response]) extends Command
  case class GetStatus(replyTo: ActorRef[ProcessingStatus]) extends Command
  case class CancelProcessing(id: Long, replyTo: ActorRef[ProcessingCancelled]) extends Command
  private case class ProcessingResponse(msg: Coordinator.Response, replyTo: ActorRef[ProcessingOutcome]) extends Command
//  private case class ProcessingEnded(id: Long, replyTo: ActorRef[ProcessingFinished]) extends Command
//  private case class ProcessingFailed(id: Long, replyTo: ActorRef[ProcessingFinished]) extends Command

  sealed trait Response
  case class ProcessingStarted(id: Long) extends Response
  case object ProcessingRejected extends Response
  sealed trait ProcessingOutcome extends Response
  case class ProcessingFinished(id: Long) extends ProcessingOutcome
  case class ProcessingFailed(id: Long) extends ProcessingOutcome
  final case class ProcessingStatus(id: Long, dataset: Option[Dataset])
  final case class ProcessingCancelled(id: Long, cause: String)
  
  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    new Scheduler(ctx).start()
  }
}

private class Scheduler private(ctx: ActorContext[Scheduler.Command]) {
  import Scheduler.*

  private val tsManager = ctx.spawn(TimeSeriesManager(), "time-series-manager")
  ctx.watch(tsManager)
//  private val communicator = ???

  private def start(): Behavior[Command] = idle(0)

  private def idle(jobId: Long): Behavior[Command] = Behaviors.receiveMessagePartial {
    case StartProcessing(d, replyTo) =>
      ctx.log.info("Start processing dataset {}", d)
      val newJobId = jobId + 1
      replyTo ! ProcessingStarted(newJobId)
      val coordinator = startNewJob(newJobId, d, replyTo)
      inProgress(newJobId, d, coordinator)
    case GetStatus(replyTo) =>
      replyTo ! ProcessingStatus(jobId, None)
      Behaviors.same
    case CancelProcessing(id, replyTo) =>
      ctx.log.warn("No dataset is currently being processed!")
      replyTo ! ProcessingCancelled(id, "Nothing changed, no dataset is currently being processed!")
      Behaviors.same
  }

  private def inProgress(jobId: Long, dataset: Dataset, coordinator: ActorRef[Coordinator.Command]): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
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
        idle(jobId)
      case ProcessingResponse(Coordinator.ProcessingFailed(id), replyTo) =>
        ctx.log.error("Failed to process dataset job={}", id)
//        replyTo ! ProcessingFailed(d)
        Behaviors.same
      case ProcessingResponse(Coordinator.ProcessingStatus(_, _), _) =>
        // ignore
        Behaviors.same
    }.receiveSignal {
      case (_, Terminated(actorRef)) =>
        ctx.log.error("Coordinator {} terminated, failed to process dataset {}", actorRef, jobId)
        idle(jobId)
    }
  
  private def startNewJob(id: Long, dataset: Dataset, replyTo: ActorRef[ProcessingOutcome]): ActorRef[Coordinator.Command] = {
    val msgAdapter = ctx.messageAdapter(ProcessingResponse(_, replyTo))
    val coordinator = ctx.spawn(Coordinator(tsManager, id, dataset, msgAdapter), s"coordinator-$id")
    ctx.watch(coordinator)
    coordinator
  }
}