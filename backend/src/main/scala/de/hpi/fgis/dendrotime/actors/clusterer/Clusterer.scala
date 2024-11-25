package de.hpi.fgis.dendrotime.actors.clusterer

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props, Terminated}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.Communicator
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.clustering.{MutablePDist, PDist}
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.Status


object Clusterer {

  def apply(coordinator: ActorRef[Coordinator.Command],
            tsManager: ActorRef[TsmProtocol.Command],
            communicator: ActorRef[Communicator.Command],
            dataset: Dataset,
            params: DendroTimeParams): Behavior[ClustererProtocol.Command] = Behaviors.setup { ctx =>

    def uninitialized(stash: StashBuffer[ClustererProtocol.Command]): Behavior[ClustererProtocol.Command] =
      Behaviors.receiveMessage {
        case ClustererProtocol.Initialize(n) =>
          stash.unstashAll(
            new Clusterer(ctx, coordinator, communicator, tsManager, n, dataset: Dataset, params).start()
          )
        case m =>
          stash.stash(m)
          Behaviors.same
      }
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(ClustererProtocol.ReportStatus, Settings(ctx.system).reportingInterval)
      Behaviors.withStash(100)(uninitialized)
    }
  }

  def props: Props = DispatcherSelector.fromConfig("dendrotime.clustering-dispatcher")
}

private class Clusterer private(ctx: ActorContext[ClustererProtocol.Command],
                                coordinator: ActorRef[Coordinator.Command],
                                communicator: ActorRef[Communicator.Command],
                                tsManager: ActorRef[TsmProtocol.Command],
                                n: Int,
                                dataset: Dataset,
                                params: DendroTimeParams,
                               ) {

  import ClustererProtocol.*

  private val settings = Settings(ctx.system)
  private val distances: MutablePDist = PDist.empty(n).mutable
  private val calculator = ctx.spawn(
    HierarchyCalculator(ctx.self, communicator, n, params),
    "hierarchy-calculator",
    HierarchyCalculator.props
  )
  ctx.watch(calculator)
  // start loading ground truth information
  if settings.ProgressIndicators.computeHierarchyQuality || settings.ProgressIndicators.computeClusterQuality then
    ctx.spawn(GroundTruthLoader(calculator, tsManager, dataset, params), "gt-loader", GroundTruthLoader.props)
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

      case m : DistanceResult if m.isApproximate =>
        ctx.log.trace("Received {} new approx distances", m.size)
        approxCount += m.size
        m.foreach { (t1, t2, dist) =>
          // TODO: might slow down the system; check if necessary or if we already guarantee that approx distances are set first
          if distances(t1, t2) != Double.PositiveInfinity then
            ctx.log.warn(
              "Distance between {} and {} was already set to {}; not overwriting with received approximate distance!",
              t1, t2, distances(t1, t2)
            )
          else
            distances(t1, t2) = dist
        }
        communicator ! Communicator.ProgressUpdate(Status.Approximating, progress(approxCount, distances.size))
        if approxCount == distances.size then
          coordinator ! Coordinator.ApproxFinished
          reg.foreach {
            _ ! ApproxDistanceMatrix(distances)
          }
        if waiting then
          calculator ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances)
          running(reg, hasWork = false, waiting = false)
        else
          running(reg, hasWork = true, waiting = false)

      case m : DistanceResult =>
        ctx.log.trace("Received {} new full distance", m.size)
        fullCount += m.size
        m.foreach { (t1, t2, dist) =>
          distances(t1, t2) = dist
        }
        communicator ! Communicator.ProgressUpdate(Status.ComputingFullDistances, progress(fullCount, distances.size))
        if fullCount == distances.size then
          coordinator ! Coordinator.FullFinished
        if waiting then
          calculator ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances)
          running(reg, hasWork = false, waiting = false)
        else
          running(reg, hasWork = true, waiting = false)

      case GetDistances if hasWork =>
        calculator ! HierarchyCalculator.ComputeHierarchy(approxCount.toInt + fullCount.toInt, distances)
        running(reg, hasWork = false, waiting = false)

      case GetDistances =>
        if fullCount >= distances.size && approxCount >= distances.size then
          // TODO: remove safety checks
          if approxCount != distances.size then
            ctx.log.error("Approx distances missing for {} pairs", distances.size - approxCount)
          if fullCount != distances.size then
            ctx.log.error("Full distances missing for {} pairs", distances.size - fullCount)
          coordinator ! Coordinator.ClusteringFinished
          Behaviors.stopped
        else
          running(reg, hasWork = false, waiting = true)

      case ReportStatus =>
        ctx.log.info(
          "[REPORT] {}/{} approx, {}/{} full distances received",
          approxCount, distances.size, fullCount, distances.size
        )
        Behaviors.same

    } receiveSignal {
      case (_, Terminated(ref)) =>
        ctx.unwatch(ref)
        running(reg - ref.unsafeUpcast[ApproxDistanceMatrix], hasWork, waiting)
        Behaviors.same
    }

  private def progress(count: Long, n: Int): Int = (count.toDouble / n * 100).toInt
}
