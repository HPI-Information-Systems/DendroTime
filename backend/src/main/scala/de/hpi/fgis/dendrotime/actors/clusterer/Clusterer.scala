package de.hpi.fgis.dendrotime.actors.clusterer

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.Communicator
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator.ClusteringFinished
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.clustering.{MutablePDist, PDist}
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVReader
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams

import java.nio.file.Path


object Clusterer {
  sealed trait Command
  case class Initialize(n: Int) extends Command
  case class ApproximateDistance(t1: Int, t2: Int, dist: Double) extends Command
  case class FullDistance(t1: Int, t2: Int, dist: Double) extends Command
  case class ReportFinished(replyTo: ActorRef[ClusteringFinished.type]) extends Command
  private[clusterer] case object GetDistances extends Command

  def apply(communicator: ActorRef[Communicator.Command], dataset: Dataset, params: DendroTimeParams): Behavior[Command] = Behaviors.setup { ctx =>
    // preload the ground truths if available
    val settings = Settings(ctx.system)
    val gtHierarchy: Option[Hierarchy] = loadGtHierarchy(dataset, params, settings.groundTruthPath)
    val gtClasses: Option[Array[String]] = loadGtClasses(dataset, settings.groundTruthPath)

    if settings.ProgressIndicators.computeHierarchyQuality && gtHierarchy.isEmpty then
      ctx.log.warn("Ground truth hierarchy not available, but hierarchy quality computation is enabled!")
    if settings.ProgressIndicators.computeClusterQuality && gtClasses.isEmpty then
      ctx.log.warn("Ground truth classes not available, but cluster quality computation is enabled!")

    def uninitialized(stash: StashBuffer[Command]): Behavior[Command] = Behaviors.receiveMessage {
      case Initialize(n) =>
        stash.unstashAll(
          new Clusterer(ctx, communicator, n, params, gtHierarchy, gtClasses).start()
        )
      case m =>
        stash.stash(m)
        Behaviors.same
    }
    Behaviors.withStash(100)(uninitialized)
  }

  private def loadGtHierarchy(dataset: Dataset, params: DendroTimeParams, gtPath: Path): Option[Hierarchy] = {
    // load the ground truth hierarchy if available
    val path = gtPath.resolve(s"${dataset.name}/hierarchy-${params.metricName}-${params.linkageName}.csv").toFile
    try
      Some(HierarchyCSVReader().parse(path))
    catch case _ =>
      None
  }
  // FIXME: implement this method and then forward the GT information to the clusterer and potentially also the HCalc
  private def loadGtClasses(dataset: Dataset, gtPath: Path): Option[Array[String]] = {
    // load the ground truth classes if available
    None
  }
}

private class Clusterer private(ctx: ActorContext[Clusterer.Command],
                                communicator: ActorRef[Communicator.Command],
                                n: Int,
                                params: DendroTimeParams,
                                gtHierarchy: Option[Hierarchy],
                                gtClasses: Option[Array[String]],
                               ) {

  import Clusterer.*

  private val distances: MutablePDist = PDist.empty(n).mutable
  private val calculatorActor = ctx.spawn(HierarchyCalculator(ctx.self, communicator, n, params), "hierarchy-calculator")
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
      // TODO: might slow down the system; check if necessary or if we already guarantee that approx distances are set first
      if distances(t1, t2) != Double.PositiveInfinity then
        ctx.log.warn(
          "Distance between {} and {} was already set to {}; not overwriting with received approximate distance!",
          t1, t2, distances(t1, t2)
        )
      distances(t1, t2) = dist
      if waiting then
        calculatorActor ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances) // NNChain creates a copy internally
        running(hasWork = false, waiting = false)
      else
        running(hasWork = true, waiting = false)
    case FullDistance(t1, t2, dist) =>
      ctx.log.debug("Received new full distance between {} and {}", t1, t2)
      fullCount += 1
      distances(t1, t2) = dist
      if waiting then
        calculatorActor ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances) // NNChain creates a copy internally
        running(hasWork = false, waiting = false)
      else
        running(hasWork = true, waiting = false)
    case GetDistances if hasWork =>
      calculatorActor ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances) // NNChain creates a copy internally
      running(hasWork = false, waiting = false)
    case GetDistances =>
      running(hasWork = false, waiting = true)
    case ReportFinished(replyTo) if waiting =>
      if hasWork then
        calculatorActor ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances) // NNChain creates a copy internally
      finished(replyTo)
    case ReportFinished(replyTo) =>
      waitingForFinish(hasWork, replyTo)
  }

  private def waitingForFinish(hasWork: Boolean, replyTo: ActorRef[ClusteringFinished.type]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetDistances if hasWork =>
        calculatorActor ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances) // NNChain creates a copy internally
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
