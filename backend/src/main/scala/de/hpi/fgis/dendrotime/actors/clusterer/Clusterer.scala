package de.hpi.fgis.dendrotime.actors.clusterer

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator.ClusteringFinished
import de.hpi.fgis.dendrotime.actors.{Communicator, TimeSeriesManager}
import de.hpi.fgis.dendrotime.clustering.{MutablePDist, PDist}
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams


object Clusterer {
  sealed trait Command
  case class Initialize(n: Int) extends Command
  case class ApproximateDistance(t1: Int, t2: Int, dist: Double) extends Command
  case class FullDistance(t1: Int, t2: Int, dist: Double) extends Command
  case class RegisterApproxDistMatrixReceiver(receiver: ActorRef[ApproxDistanceMatrix]) extends Command
  case class ReportFinished(replyTo: ActorRef[ClusteringFinished.type]) extends Command
  private[clusterer] case object GetDistances extends Command

  case class ApproxDistanceMatrix(distances: PDist)

  def apply(tsManager: ActorRef[TimeSeriesManager.Command],
            communicator: ActorRef[Communicator.Command],
            dataset: Dataset,
            params: DendroTimeParams): Behavior[Command] = Behaviors.setup { ctx =>

    def uninitialized(stash: StashBuffer[Command]): Behavior[Command] = Behaviors.receiveMessage {
      case Initialize(n) =>
        stash.unstashAll(
          new Clusterer(ctx, communicator, tsManager, n, dataset: Dataset, params).start()
        )
      case m =>
        stash.stash(m)
        Behaviors.same
    }

    Behaviors.withStash(100)(uninitialized)
  }
}

private class Clusterer private(ctx: ActorContext[Clusterer.Command],
                                communicator: ActorRef[Communicator.Command],
                                tsManager: ActorRef[TimeSeriesManager.Command],
                                n: Int,
                                dataset: Dataset,
                                params: DendroTimeParams,
                               ) {

  import Clusterer.*

  private val settings = Settings(ctx.system)
  private val distances: MutablePDist = PDist.empty(n).mutable
  private val calculator = ctx.spawn(
    HierarchyCalculator(ctx.self, communicator, n, params),
    "hierarchy-calculator"
  )
  ctx.watch(calculator)
  // start loading ground truth information
  if settings.ProgressIndicators.computeHierarchyQuality || settings.ProgressIndicators.computeClusterQuality then
    ctx.spawn(GroundTruthLoader(calculator, tsManager, dataset, params), "gt-loader")
  // debug counters
  private var approxCount = 0L
  private var fullCount = 0L

  private def start(): Behavior[Command] = running(Set.empty, false, false)

  private def running(reg: Set[ActorRef[ApproxDistanceMatrix]], hasWork: Boolean, waiting: Boolean): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
      case Initialize(newN) if newN == n =>
        ctx.log.warn("Received duplicated initialization message!")
        Behaviors.same

      case Initialize(newN) =>
        ctx.log.error("Clusterer was already initialized with n={}, but got new initialization request with n={}", n, newN)
        Behaviors.stopped

      case RegisterApproxDistMatrixReceiver(receiver) =>
        ctx.watch(receiver)
        running(reg + receiver, hasWork, waiting)

      case ApproximateDistance(t1, t2, dist) =>
        ctx.log.trace("Received new approx distance between {} and {}", t1, t2)
        approxCount += 1
        // TODO: might slow down the system; check if necessary or if we already guarantee that approx distances are set first
        if distances(t1, t2) != Double.PositiveInfinity then
          ctx.log.warn(
            "Distance between {} and {} was already set to {}; not overwriting with received approximate distance!",
            t1, t2, distances(t1, t2)
          )
          Behaviors.same
        else
          distances(t1, t2) = dist
          if approxCount == distances.size then
            reg.foreach {
              _ ! ApproxDistanceMatrix(distances)
            }
          if waiting then
            calculator ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances)
            running(reg, hasWork = false, waiting = false)
          else
            running(reg, hasWork = true, waiting = false)

      case FullDistance(t1, t2, dist) =>
        ctx.log.trace("Received new full distance between {} and {}", t1, t2)
        fullCount += 1
        distances(t1, t2) = dist
        if waiting then
          calculator ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances)
          running(reg, hasWork = false, waiting = false)
        else
          running(reg, hasWork = true, waiting = false)

      case GetDistances if hasWork =>
        calculator ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances)
        running(reg, hasWork = false, waiting = false)

      case GetDistances =>
        running(reg, hasWork = false, waiting = true)

      case ReportFinished(replyTo) if waiting =>
        if hasWork then
          calculator ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances)
        finished(replyTo)

      case ReportFinished(replyTo) =>
        ctx.log.debug("Not yet finished, waiting for hierarchy calculation to terminate before shutting down")
        waitingForFinish(hasWork, replyTo)

    }.receiveSignal {
      case (_, Terminated(ref)) =>
        ctx.unwatch(ref)
        running(reg - ref.unsafeUpcast[ApproxDistanceMatrix], hasWork, waiting)
        Behaviors.same
    }

  private def waitingForFinish(hasWork: Boolean, replyTo: ActorRef[ClusteringFinished.type]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetDistances if hasWork =>
        calculator ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances)
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
