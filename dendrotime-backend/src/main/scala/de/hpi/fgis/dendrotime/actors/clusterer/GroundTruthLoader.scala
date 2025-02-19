package de.hpi.fgis.dendrotime.actors.clusterer

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVReader
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams

import scala.language.postfixOps

object GroundTruthLoader {
  sealed trait Command
  private case object LoadHierarchyGroundTruth extends Command
  private case object LoadClassesGroundTruth extends Command
  private case class ClassLabelResponseWrapper(m: TsmProtocol.DatasetClassLabelsResponse) extends Command

  def apply(hierarchyCalculator: ActorRef[HierarchyCalculator.Command],
            tsManager: ActorRef[TsmProtocol.Command],
            dataset: Dataset,
            params: DendroTimeParams): Behavior[Command] =
    Behaviors.setup ( ctx =>
      Behaviors.withTimers(timer =>
        new GroundTruthLoader(ctx, timer, hierarchyCalculator, tsManager, dataset, params).start()
      )
    )

  def props: Props = DispatcherSelector.blocking()
}

class GroundTruthLoader(ctx: ActorContext[GroundTruthLoader.Command],
                        timer: TimerScheduler[GroundTruthLoader.Command],
                        hierarchyCalculator: ActorRef[HierarchyCalculator.Command],
                        tsManager: ActorRef[TsmProtocol.Command],
                        dataset: Dataset,
                        params: DendroTimeParams) {

  import GroundTruthLoader.*

  private val settings = Settings(ctx.system)
  private val labelResponseAdapter = ctx.messageAdapter(ClassLabelResponseWrapper.apply)

  def start(): Behavior[Command] = {
    val delay = settings.ProgressIndicators.loadingDelay

    // defer loading of the ground truth information to speed up the startup
    // but always load the hierarchy ground truth first if enabled
    if settings.ProgressIndicators.computeHierarchyQuality then
      timer.startSingleTimer(LoadHierarchyGroundTruth, delay)
      waiting()

    else if settings.ProgressIndicators.computeClusterQuality then
      timer.startSingleTimer(LoadClassesGroundTruth, delay)
      waiting()

    else
      Behaviors.stopped
  }

  private def waiting(gtHierarchy: Option[Hierarchy] = None): Behavior[Command] = Behaviors.receiveMessage {
    case LoadHierarchyGroundTruth =>
      ctx.log.debug("Loading ground truth hierarchy")
      val gth = loadGtHierarchy()
      if gth.isEmpty then
        ctx.log.warn("Ground truth hierarchy not available, but hierarchy quality computation is enabled!")

      // also load the class labels if needed
      if settings.ProgressIndicators.computeClusterQuality then
        ctx.log.debug("Loading ground truth classes")
        tsManager ! TsmProtocol.GetDatasetClassLabels(dataset.id, labelResponseAdapter)
        waiting(gtHierarchy = gth)
      else
        hierarchyCalculator ! HierarchyCalculator.GroundTruthLoaded(gth, None)
        Behaviors.stopped

    case LoadClassesGroundTruth =>
      ctx.log.debug("Loading ground truth classes")
      tsManager ! TsmProtocol.GetDatasetClassLabels(dataset.id, labelResponseAdapter)
      Behaviors.same

    case ClassLabelResponseWrapper(TsmProtocol.DatasetClassLabels(labels)) =>
      hierarchyCalculator ! HierarchyCalculator.GroundTruthLoaded(gtHierarchy, Some(labels))
      Behaviors.stopped

    case ClassLabelResponseWrapper(TsmProtocol.DatasetClassLabelsNotFound) =>
      ctx.log.warn("Ground truth classes not available, but cluster quality computation is enabled!")
      hierarchyCalculator ! HierarchyCalculator.GroundTruthLoaded(gtHierarchy, None)
      Behaviors.stopped
  }

  private def loadGtHierarchy(): Option[Hierarchy] = {
    // load the ground truth hierarchy if available
    val gtPath = settings.groundTruthPath
    val path = gtPath.resolve(s"${dataset.name}/hierarchy-${params.distanceName}-${params.linkageName}.csv").toFile
    try
      Some(HierarchyCSVReader.parse(path))
    catch case e =>
      ctx.log.warn("Failed to load ground truth hierarchy from path {}: {}", path.getAbsolutePath, e.getMessage)
      None
  }
}
