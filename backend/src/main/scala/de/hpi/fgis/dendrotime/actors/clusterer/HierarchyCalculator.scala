package de.hpi.fgis.dendrotime.actors.clusterer

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.Communicator
import de.hpi.fgis.dendrotime.actors.Communicator.NewHierarchy
import de.hpi.fgis.dendrotime.clustering.{PDist, hierarchy}

private[clusterer] object HierarchyCalculator {
  sealed trait Command
  case class ComputeHierarchy(distances: PDist) extends Command
  case object ReportRuntime extends Command

  def apply(clusterer: ActorRef[Clusterer.Command],
            communicator: ActorRef[Communicator.Command],
            n: Int
           ): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(ReportRuntime, Settings(ctx.system).reportingInterval)
      new HierarchyCalculator(ctx, clusterer, communicator, n).start()
    }
  }
}

private[clusterer] class HierarchyCalculator(ctx: ActorContext[HierarchyCalculator.Command],
                                             clusterer: ActorRef[Clusterer.Command],
                                             communicator: ActorRef[Communicator.Command],
                                             n: Int) {

  import HierarchyCalculator.*

  private val linkage = Settings(ctx.system).linkage
  // debug counters
  private var runtime = 0L
  private var computations = 0

  private def start(): Behavior[Command] = {
    clusterer ! Clusterer.GetDistances
    running(HierarchyState.empty(n))
  }

  private def running(state: HierarchyState): Behavior[Command] = Behaviors.receiveMessage {
    case ComputeHierarchy(distances) =>
      val newState = computeHierarchy(state, distances)
      clusterer ! Clusterer.GetDistances
      running(newState)
    case ReportRuntime =>
      val newComps = state.computations - computations
      ctx.log.info("Average computation time for the last {} hierarchies: {} ms", newComps, runtime / newComps)
      runtime = 0L
      computations = state.computations
      Behaviors.same
  }

  private def computeHierarchy(state: HierarchyState, distances: PDist): HierarchyState = {
    val start = System.currentTimeMillis()
    val h = hierarchy.computeHierarchy(distances, linkage)
    val newState = state.newHierarchy(h)
    runtime += System.currentTimeMillis() - start
//    ctx.log.warn("[PROG-REPORT] Changes for iteration {}: {}", newState.computations, newState.similarities(newState.computations - 1))
    communicator ! NewHierarchy(h, newState.similarities)
    newState
  }
}
