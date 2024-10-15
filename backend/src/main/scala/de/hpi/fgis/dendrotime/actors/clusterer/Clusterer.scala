package de.hpi.fgis.dendrotime.actors.clusterer

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.actors.Communicator
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator.ClusteringFinished
import de.hpi.fgis.dendrotime.clustering.{MutablePDist, PDist}


object Clusterer {
  sealed trait Command
  case class Initialize(n: Int) extends Command
  case class ApproximateDistance(t1: Int, t2: Int, dist: Double) extends Command
  case class FullDistance(t1: Int, t2: Int, dist: Double) extends Command
  case class ReportFinished(replyTo: ActorRef[ClusteringFinished.type]) extends Command
  private[clusterer] case object GetDistances extends Command

  def apply(communicator: ActorRef[Communicator.Command]): Behavior[Command] = {
    def uninitialized(stash: StashBuffer[Command]): Behavior[Command] = Behaviors.receiveMessage {
      case Initialize(n) =>
        stash.unstashAll(Behaviors.setup(ctx =>
          new Clusterer(ctx, communicator, n).start()
        ))
      case m =>
        stash.stash(m)
        Behaviors.same
    }

    Behaviors.withStash(100)(uninitialized)
  }
}

private class Clusterer private(ctx: ActorContext[Clusterer.Command],
                                communicator: ActorRef[Communicator.Command],
                                n: Int) {

  import Clusterer.*

  private val distances: MutablePDist = PDist.empty(n).mutable
  private val calculatorActor = ctx.spawn(HierarchyCalculator(ctx.self, communicator, n), "hierarchy-calculator")
  // debug counters
  private var approxCount = 0L
  private var fullCount = 0L

  private def start(): Behavior[Command] = running(false, false)

  private def running(hasWork: Boolean, waiting: Boolean): Behavior[Command] = Behaviors.receiveMessage {
    case Initialize(newN) if newN == n =>
      ctx.log.warn("Received duplicated initialization message!")
      Behaviors.same
    case Initialize(newN) =>
      ctx.log.error("Clusterer was already initialized with n={}, but got new initialization request with n={}", n, newN)
      Behaviors.stopped
    case ApproximateDistance(t1, t2, dist) =>
      ctx.log.debug("Received new approx distance between {} and {}", t1, t2)
      approxCount += 1
      distances(t1, t2) = dist
      if waiting then
        calculatorActor ! HierarchyCalculator.ComputeHierarchy(distances) // NNChain creates a copy internally
        running(hasWork = false, waiting = false)
      else
        running(hasWork = true, waiting = false)
    case FullDistance(t1, t2, dist) =>
      ctx.log.debug("Received new full distance between {} and {}", t1, t2)
      fullCount += 1
      distances(t1, t2) = dist
      if waiting then
        calculatorActor ! HierarchyCalculator.ComputeHierarchy(distances) // NNChain creates a copy internally
        running(hasWork = false, waiting = false)
      else
        running(hasWork = true, waiting = false)
    case GetDistances if hasWork =>
      calculatorActor ! HierarchyCalculator.ComputeHierarchy(distances) // NNChain creates a copy internally
      running(hasWork = false, waiting = false)
    case GetDistances =>
      running(hasWork = false, waiting = true)
    case ReportFinished(replyTo) if waiting =>
      if hasWork then
        calculatorActor !HierarchyCalculator.ComputeHierarchy(distances) // NNChain creates a copy internally
      finished(replyTo)
    case ReportFinished(replyTo) =>
      waitingForFinish(hasWork, replyTo)
  }

  private def waitingForFinish(hasWork: Boolean, replyTo: ActorRef[ClusteringFinished.type]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetDistances if hasWork =>
        calculatorActor ! HierarchyCalculator.ComputeHierarchy(distances) // NNChain creates a copy internally
        waitingForFinish(hasWork = false, replyTo = replyTo)
      case GetDistances =>
        finished(replyTo)
      case m =>
        ctx.log.warn("Received unexpected message while waiting for finish ({})", m)
        Behaviors.same
    }

  private def finished(replyTo: ActorRef[ClusteringFinished.type]): Behavior[Command] = {
    if approxCount != distances.size then
      ctx.log.error("Approx distances missing for {} pairs", distances.size - approxCount)
    if fullCount != distances.size then
      ctx.log.error("Full distances missing for {} pairs", distances.size - fullCount)
    replyTo ! ClusteringFinished
    Behaviors.stopped
  }
}
