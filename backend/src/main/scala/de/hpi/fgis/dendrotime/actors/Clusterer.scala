package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.Communicator.NewHierarchy
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator.ClusteringFinished
import de.hpi.fgis.dendrotime.clustering.{MutablePDist, PDist, hierarchy}


object Clusterer {
  sealed trait Command
  case class Initialize(n: Int) extends Command
  case class ApproximateDistance(t1: Int, t2: Int, dist: Double) extends Command
  case class FullDistance(t1: Int, t2: Int, dist: Double) extends Command
  case class ReportFinished(replyTo: ActorRef[ClusteringFinished.type]) extends Command

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

    Behaviors.withStash(1000)(uninitialized)
  }

  // the clusterer, for now, skips some hierarchy computations for speed reasons
  // once we add the incremental approach, this should not be necessary anymore
  private final val INTERVAL = 500L
}

private class Clusterer private(ctx: ActorContext[Clusterer.Command],
                                communicator: ActorRef[Communicator.Command],
                                n: Int) {
  import Clusterer.*

  private val linkage = Settings(ctx.system).linkage
  private val distances: MutablePDist = PDist.empty(n).mutable
  // debug counters
  private var approxCount = 0L
  private var fullCount = 0L

  private def start(): Behavior[Command] = running(System.currentTimeMillis())

  private def running(lastComputation: Long): Behavior[Command] = Behaviors.receiveMessage{
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
      potentiallyComputeHierarchy(lastComputation)
    case FullDistance(t1, t2, dist) =>
      ctx.log.debug("Received new full distance between {} and {}", t1, t2)
      fullCount += 1
      distances(t1, t2) = dist
      potentiallyComputeHierarchy(lastComputation)
    case ReportFinished(replyTo) if System.currentTimeMillis() - lastComputation < INTERVAL =>
      if approxCount != distances.size then
        ctx.log.error("Approx distances missing for {} pairs", distances.size - approxCount)
      if fullCount != distances.size then
        ctx.log.error("Full distances missing for {} pairs", distances.size - fullCount)
      replyTo ! ClusteringFinished
      Behaviors.stopped
    case ReportFinished(_) =>
      Behaviors.same
  }

  private def potentiallyComputeHierarchy(lastComputation: Long): Behavior[Command] = {
    if System.currentTimeMillis() - lastComputation >= INTERVAL then
      val h = hierarchy.computeHierarchy(distances, linkage)
//    ctx.log.debug("Computed new hierarchy:\n{}", h)
      communicator ! NewHierarchy(h)
      running(System.currentTimeMillis())
    else
      running(lastComputation)
  }
}
