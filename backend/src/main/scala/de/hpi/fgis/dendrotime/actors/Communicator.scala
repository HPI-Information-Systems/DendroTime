package de.hpi.fgis.dendrotime.actors

import scala.concurrent.duration.*
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.model.StateModel.{DendrogramTree, ProgressMessage, Status}

import scala.language.postfixOps
import scala.math.Ordering.Implicits.infixOrderingOps

object Communicator {
  
  sealed trait Command
  final case class NewStatus(status: Status) extends Command
  final case class ProgressUpdate(status: Status, progress: Int) extends Command
  final case class NewHierarchy(hierarchy: Hierarchy) extends Command
  final case class GetProgress(replyTo: ActorRef[ProgressMessage]) extends Command
  private case object Tick extends Command
  
  def apply(): Behavior[Command] = Behaviors.setup { ctx => Behaviors.withTimers { timers =>
    timers.startTimerAtFixedRate(Tick, 1 second)
    val startProgress = Map[Status, Int](
      Status.Initializing -> 20,
      Status.Approximating -> 0,
      Status.ComputingFullDistances -> 0,
      Status.Finalizing -> 0,
      Status.Finished -> 100
    )
    new Communicator(ctx).running(Status.Initializing, startProgress, Hierarchy.empty)
  }}
}

private class Communicator private (ctx: ActorContext[Communicator.Command]) {
  import Communicator.*
  
  private def running(status: Status, progress: Map[Status, Int], hierarchy: Hierarchy): Behavior[Command] =
    Behaviors.receiveMessage {
      case NewStatus(newStatus) =>
        // set all previous progresses to 100
        val newP = for (k, v) <- progress yield (k, if k < newStatus then 100 else v)
        ctx.log.info("Updating progress bc. state change to {}: {}", newStatus, newP)
        running(newStatus, newP, hierarchy)
      case ProgressUpdate(s, p) =>
        ctx.log.debug("({}) New progress ({}): {}", status, s, p)
        running(status, progress.updated(s, p), hierarchy)
      case NewHierarchy(h) =>
        running(status, progress, h)
      case GetProgress(replyTo) =>
        replyTo ! ProgressMessage.CurrentProgress(status, progress(status), hierarchy)
        Behaviors.same
      case Tick =>
        ctx.log.info("Current status: {}, progress: {}, hierarchy: {}", status, progress, hierarchy)
        Behaviors.same
    }
}
