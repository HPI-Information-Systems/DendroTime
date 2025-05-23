package de.hpi.fgis.dendrotime.actors.clusterer

import akka.actor.typed.*
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.Communicator
import de.hpi.fgis.dendrotime.actors.Communicator.NewHierarchy
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.clustering.{PDist, hierarchy}
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams

private[clusterer] object HierarchyCalculator {
  sealed trait Command
  case class ComputeHierarchy(index: Int, distances: PDist) extends Command
  case class GroundTruthLoaded(gtHierarchy: Option[Hierarchy], gtClassLabels: Option[Array[String]]) extends Command
  private case object ReportRuntime extends Command

  def apply(clusterer: ActorRef[ClustererProtocol.Command],
            communicator: ActorRef[Communicator.Command],
            n: Int,
            params: DendroTimeParams,
           ): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(ReportRuntime, Settings(ctx.system).reportingInterval)
      new HierarchyCalculator(ctx, clusterer, communicator, n, params).start()
    }
  }

  def props: Props = DispatcherSelector.fromConfig("dendrotime.clustering-dispatcher")
}

private[clusterer] class HierarchyCalculator(ctx: ActorContext[HierarchyCalculator.Command],
                                             clusterer: ActorRef[ClustererProtocol.Command],
                                             communicator: ActorRef[Communicator.Command],
                                             n: Int,
                                             params: DendroTimeParams) {

  import HierarchyCalculator.*

  private val settings: Settings = Settings(ctx.system)
  private val state: HierarchyState = {
    import settings.given

    if settings.ProgressIndicators.disabled then
      HierarchyState.nonTracking(n)
    else
      HierarchyState.tracking(
        n,
        settings.ProgressIndicators.hierarchySimilarityConfig,
        settings.ProgressIndicators.hierarchyQualityConfig,
        settings.ProgressIndicators.clusterQualityMethod
      )
  }
  // debug counters
  private var runtime = 0L
  private var computations = 0

  private def start(): Behavior[Command] = {
    clusterer ! Clusterer.GetDistances
    running()
  }

  private def running(): Behavior[Command] = Behaviors.receiveMessage[Command] {
    case ComputeHierarchy(index, distances) =>
      computeHierarchy(index, distances)
      communicator ! NewHierarchy(state.toClusteringState)
      clusterer ! Clusterer.GetDistances
      Behaviors.same

    case GroundTruthLoaded(gtHierarchy, gtClassLabels) =>
      ctx.log.debug("Ground truth updated")
      try
        state.setGtHierarchy(gtHierarchy)
      catch case e: Exception =>
        ctx.log.error("Failed to set ground truth hierarchy:", e)
      try
        state.setGtClasses(gtClassLabels)
      catch case e: Exception =>
        ctx.log.error("Failed to set ground truth classes:", e)
      Behaviors.same

    case ReportRuntime =>
      val newComps = state.computations - computations
      if newComps > 0 then
        ctx.log.info("[REPORT] Average computation time for the last {} hierarchies: {} ms", newComps, runtime / newComps)
      runtime = 0L
      computations = state.computations
      Behaviors.same

  } receiveSignal {
    case (_, PostStop) =>
      val newComps = state.computations - computations
      if newComps > 0 then
        ctx.log.info("[REPORT] Average computation time for the last {} hierarchies: {} ms", newComps, runtime / newComps)

      ctx.log.info("HierarchyCalculator stopped, releasing resources")
      state.dispose()
      Behaviors.stopped
  }

  private def computeHierarchy(index: Int, distances: PDist): Unit = {
    val start = System.currentTimeMillis()
    val h = hierarchy.computeHierarchy(distances, params.linkage)
    state.newHierarchy(index, h)
    runtime += System.currentTimeMillis() - start
  }
}
