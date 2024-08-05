package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.Communicator.NewHierarchy
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.clustering.{MutablePDist, PDist, hierarchy}


object Clusterer {
  sealed trait Command
  case class Initialize(n: Int) extends Command
  case class ApproximateDistance(t1: Int, t2: Int, dist: Double) extends Command
  case class FullDistance(t1: Int, t2: Int, dist: Double) extends Command

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
}

private class Clusterer private(ctx: ActorContext[Clusterer.Command],
                                communicator: ActorRef[Communicator.Command],
                                n: Int) {
  import Clusterer.*

  private val linkage = Settings(ctx.system).linkage
  private val distances: MutablePDist = PDist.empty(n).mutable

  private def start(): Behavior[Command] = running()

  private def running(): Behavior[Command] = Behaviors.receiveMessage{
    case Initialize(newN) if newN == n =>
      ctx.log.warn("Received duplicated initialization message!")
      Behaviors.same
    case Initialize(newN) =>
      ctx.log.error("Clusterer was already initialized with n={}, but got new initialization request with n={}", n, newN)
      Behaviors.stopped
    case ApproximateDistance(t1, t2, dist) =>
      ctx.log.debug("Received new approx distance between {} and {}", t1, t2)
      distances(t1, t2) = dist
      computeHierarchy()
      Behaviors.same
    case FullDistance(t1, t2, dist) =>
      ctx.log.debug("Received new full distance between {} and {}", t1, t2)
      distances(t1, t2) = dist
      computeHierarchy()
      Behaviors.same
  }

  private def computeHierarchy(): Hierarchy = {
    val h = hierarchy.computeHierarchy(distances, linkage)
//    ctx.log.debug("Computed new hierarchy:\n{}", h)
    communicator ! NewHierarchy(h)
    h
  }
}
