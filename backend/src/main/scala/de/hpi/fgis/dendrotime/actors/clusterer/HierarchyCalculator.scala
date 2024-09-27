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
            communicator: ActorRef[Communicator.Command]
           ): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(ReportRuntime, Settings(ctx.system).reportingInterval)
      new HierarchyCalculator(ctx, clusterer, communicator).start()
    }
  }
}

private[clusterer] class HierarchyCalculator(ctx: ActorContext[HierarchyCalculator.Command],
                                             clusterer: ActorRef[Clusterer.Command],
                                             communicator: ActorRef[Communicator.Command]) {

  import HierarchyCalculator.*

  private val linkage = Settings(ctx.system).linkage
  // debug counters
  private var runtime = 0L
  private var computations = 0L

  private def start(): Behavior[Command] = {
    clusterer ! Clusterer.GetDistances
    running()
  }

  private def running(): Behavior[Command] = Behaviors.receiveMessage {
    case ComputeHierarchy(distances) =>
      computeHierarchy(distances)
      clusterer ! Clusterer.GetDistances
      Behaviors.same
    case ReportRuntime =>
      ctx.log.info("Average computation time for the last {} hierarchies: {} ms", computations, runtime / computations)
      runtime = 0L
      computations = 0L
      Behaviors.same
  }

  private def computeHierarchy(distances: PDist): Unit = {
    val start = System.currentTimeMillis()
    val h = hierarchy.computeHierarchy(distances, linkage)
    runtime += System.currentTimeMillis() - start
    computations += 1
    //    ctx.log.debug("Computed new hierarchy:\n{}", h)
    communicator ! NewHierarchy(h)
  }
}
