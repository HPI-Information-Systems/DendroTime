package de.hpi.fgis.dendrotime.actors.clusterer

import akka.actor.typed.*
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.Communicator
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.clustering.{MutablePDist, PDist}
import de.hpi.fgis.dendrotime.io.CSVWriter
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.structures.{CompactPairwiseBitset, Status}


object Clusterer {

  private[clusterer] case object GetDistances extends ClustererProtocol.Command

  private case object ReportStatus extends ClustererProtocol.Command

  def apply(
             dataset: Dataset,
             params: DendroTimeParams,
             coordinator: ActorRef[Coordinator.Command],
             tsManager: ActorRef[TsmProtocol.Command],
             communicator: ActorRef[Communicator.Command]
           ): Behavior[ClustererProtocol.Command] = Behaviors.setup { ctx =>

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
      timers.startTimerWithFixedDelay(ReportStatus, Settings(ctx.system).reportingInterval)
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

  import Clusterer.*
  import ClustererProtocol.*

  private val settings = Settings(ctx.system)
  private val distances: MutablePDist = PDist.empty(n).mutable
  private val fullMask = CompactPairwiseBitset.ofDim(n)
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
  private var approxCount = 0
  private var fullCount = 0

  private def start(): Behavior[Command] = running(Set.empty, false, false)

  private def running(reg: Set[ActorRef[DistanceMatrix]], hasWork: Boolean, waiting: Boolean): Behavior[Command] =
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

      case m : DistanceResult if m.isEstimated =>
        ctx.log.debug("Received {} new estimated distances", m.size)
        m.foreach { (t1, t2, dist) =>
          // required to prevent overwriting full distances that were computed by the fallback strategy:
          if !fullMask(t1, t2) then
            distances(t1, t2) = dist
//          else
//            ctx.log.warn(
//              "Distance between {} and {} was already set to {}; not overwriting with received estimated distance {}!",
//              t1, t2, distances(t1, t2), dist
//            )
        }
        Behaviors.same

      case m : DistanceResult if m.isApproximate =>
        ctx.log.trace("Received {} new approx distances", m.size)
        approxCount += m.size
        m.foreach { (t1, t2, dist) =>
          // TODO: might slow down the system; check if necessary or if we already guarantee that approx distances are set first
          if !fullMask(t1, t2) then
            distances(t1, t2) = dist
          else
            ctx.log.warn(
              "Distance between {} and {} was already set to {}; not overwriting with received approximate distance {}!",
              t1, t2, distances(t1, t2), dist
            )
        }
        communicator ! Communicator.ProgressUpdate(Status.Approximating, progress(approxCount, distances.size))
        val nextBehavior = nextWithHCDispatch(reg, waiting)
        if approxCount == distances.size then
          coordinator ! Coordinator.ApproxFinished
          ctx.log.debug("Received all approx distances")
          // FIXME: copy?
          reg.foreach {
            _ ! DistanceMatrix(distances)
          }
          saveDistanceMatrix("approx")
        nextBehavior

      case m : DistanceResult =>
        ctx.log.trace("Received {} new full distances", m.size)
        fullCount += m.size
        m.foreach { (t1, t2, dist) =>
          distances(t1, t2) = dist
          fullMask.add(t1, t2)
        }
        communicator ! Communicator.ProgressUpdate(Status.ComputingFullDistances, progress(fullCount, distances.size))
        val nextBehavior = nextWithHCDispatch(reg, waiting)
        if fullCount == distances.size then
          coordinator ! Coordinator.FullFinished
          ctx.log.debug("Received all full distances")
          if fullMask.size != fullCount then
            ctx.log.error("Full mask size {} does not match full count {}", fullMask.size, fullCount)
          saveDistanceMatrix("full")
        nextBehavior

      case GetCurrentDistanceMatrix(replyTo) =>
        replyTo ! DistanceMatrix(distances)
        Behaviors.same

      case GetDistances if hasWork =>
        calculator ! HierarchyCalculator.ComputeHierarchy(approxCount + fullCount, distances)
        running(reg, hasWork = false, waiting = false)

      case GetDistances =>
        if fullCount >= distances.size && approxCount >= distances.size then
          ctx.log.info("Clustering finished, stopping")
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
        running(reg - ref.unsafeUpcast[DistanceMatrix], hasWork, waiting)
        Behaviors.same
    }

  private def nextWithHCDispatch(reg: Set[ActorRef[DistanceMatrix]], waiting: Boolean): Behavior[Command] =
    if waiting then
      calculator ! HierarchyCalculator.ComputeHierarchy(approxCount + fullCount, distances)
      running(reg, hasWork = false, waiting = false)
    else
      running(reg, hasWork = true, waiting = false)

  private def progress(count: Int, n: Int): Int = (count.toDouble / n * 100).toInt

  private def saveDistanceMatrix(tpe: String): Unit = {
    if settings.storeDistances then
      val datasetPath = settings.resolveResultsFolder(dataset, params)
      datasetPath.toFile.mkdirs()
      val file = datasetPath.resolve(s"$tpe-distances.csv").toFile
      ctx.log.info("Saving {} distance matrix to file to {}", tpe, file)
      CSVWriter.write(file, distances.matrixView)
  }
}
