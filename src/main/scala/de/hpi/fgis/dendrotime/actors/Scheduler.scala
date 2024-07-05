package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
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

  private def start(): Behavior[Command] = running(0, None)
  
  private def running(jobId: Long, dataset: Option[Dataset]): Behavior[Command] = Behaviors.receiveMessage {
    case StartProcessing(d, replyTo) =>
      if dataset.isEmpty then
        ctx.log.info("Start processing dataset {}", d)
        val newJobId = jobId + 1
        replyTo ! ProcessingStarted(newJobId)
//        ctx.scheduleOnce(10.seconds, ctx.self, ProcessingEnded(newJobId, replyTo))
        startNewJob(newJobId, d, replyTo)
        running(newJobId, Some(d))
      else
        ctx.log.warn("Already processing a dataset, ignoring request to start processing dataset {}")
        replyTo ! ProcessingRejected
        Behaviors.same
    case ProcessingResponse(Coordinator.ProcessingEnded(d), replyTo) =>
      ctx.log.info("Successfully processed dataset {}", d)
      replyTo ! ProcessingFinished(d)
      running(jobId, None)
    case ProcessingResponse(Coordinator.ProcessingFailed(d), replyTo) =>
      ctx.log.error("Failed to process dataset {}", d)
      replyTo ! ProcessingFailed(d)
      running(jobId, None)
    case CancelProcessing(id, replyTo) =>
      dataset match
        case Some(d) if id == jobId =>
          ctx.log.info("Cancelled processing of dataset {}", d)
          replyTo ! ProcessingCancelled(jobId, "Cancelled by user")
          running(jobId, None)
        case _ =>
          ctx.log.warn("No dataset with id {} is currently being processed", id)
          replyTo ! ProcessingCancelled(id, "No dataset with id $id is currently being processed")
          Behaviors.same
    case GetStatus(replyTo) =>
      replyTo ! ProcessingStatus(jobId, dataset)
      Behaviors.same
  }
  
  private def startNewJob(id: Long, dataset: Dataset, replyTo: ActorRef[ProcessingOutcome]): Unit = {
    val msgAdapter = ctx.messageAdapter(ProcessingResponse(_, replyTo))
    val coordinator = ctx.spawn(Coordinator(tsManager, id, dataset, msgAdapter), s"coordinator-$id")
    ctx.watch(coordinator)
  }
}