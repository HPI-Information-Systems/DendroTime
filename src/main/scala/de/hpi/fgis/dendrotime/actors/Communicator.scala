package de.hpi.fgis.dendrotime.actors

import scala.concurrent.duration.*
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.model.StateModel.{ProgressMessage, Status}

import scala.language.postfixOps

object Communicator {
  
  sealed trait Command
  final case class NewStatus(status: Status) extends Command
  final case class ProgressUpdate(progress: Int) extends Command
  final case class NewHierarchy(hierarchy: Hierarchy) extends Command
  final case class GetProgress(replyTo: ActorRef[ProgressMessage]) extends Command
  private case object Tick extends Command
  
  def apply(): Behavior[Command] = Behaviors.setup { ctx => Behaviors.withTimers { timers =>
    timers.startTimerAtFixedRate(Tick, 1 second)
    new Communicator(ctx).running(Status.Initializing, 0, Hierarchy.empty)
  }}
}

private class Communicator private (ctx: ActorContext[Communicator.Command]) {
  import Communicator.*
  
  private def running(status: Status, progress: Int, hierarchy: Hierarchy): Behavior[Command] =
    Behaviors.receiveMessage {
      case NewStatus(newStatus) =>
        running(newStatus, progress = 0, hierarchy)
      case ProgressUpdate(progress) =>
        running(status, progress, hierarchy)
      case NewHierarchy(hierarchy) =>
        running(status, progress, hierarchy)
      case GetProgress(replyTo) =>
        replyTo ! ProgressMessage.CurrentProgress(status, progress, hierarchy)
        Behaviors.same
      case Tick =>
        ctx.log.info("Current status: {}, progress: {}, hierarchy: {}", status, progress, hierarchy)
        Behaviors.same
    }
}
