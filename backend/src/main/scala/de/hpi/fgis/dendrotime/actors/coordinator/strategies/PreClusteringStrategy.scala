package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, PostStop}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol.{DistanceMatrix, GetCurrentDistanceMatrix, RegisterApproxDistMatrixReceiver}
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyParameters.InternalStrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol.{GetTSIndexMapping, TSIndexMappingResponse}
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, computeHierarchy}
import de.hpi.fgis.dendrotime.structures.CompactPairwiseBitset
import de.hpi.fgis.dendrotime.structures.strategies.{OrderedPreClusteringWorkGenerator, WorkGenerator}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object PreClusteringStrategy extends StrategyFactory {

  private case class TSIndexMapping(mapping: Map[TsId, Int]) extends StrategyCommand
  private case class ApproxDistances(dists: PDist) extends StrategyCommand
  private case class CurrentDistanceMatrix(dists: PDist) extends StrategyCommand
  private case class PreClustersGenerated(preClusters: Array[Array[Int]]) extends StrategyCommand

  override def apply(params: InternalStrategyParameters): Behavior[StrategyCommand] =
    new Initializer(params).start()

  private class Initializer(params: InternalStrategyParameters) extends Strategy(params) with ProcessedTrackingMixin {

    private val tsIndexMappingAdapter = ctx.messageAdapter[TSIndexMappingResponse](m => TSIndexMapping(m.mapping))
    private val approxDistancesAdapter = ctx.messageAdapter[DistanceMatrix](m => ApproxDistances(m.distances))

    // Executor for internal futures (CPU-heavy work)
    private given ExecutionContext = ctx.system.dispatchers.lookup(DispatcherSelector.blocking())

    override def start(): Behavior[StrategyCommand] = {
      params.tsManager ! GetTSIndexMapping(params.dataset.id, tsIndexMappingAdapter)
      params.clusterer ! RegisterApproxDistMatrixReceiver(approxDistancesAdapter)
      collecting(None, None)
    }

    private def collecting(mapping: Option[Map[TsId, Int]], dists: Option[PDist]): Behavior[StrategyCommand] = Behaviors.receiveMessage {
      case AddTimeSeries(_) => Behaviors.same // ignore

      case m: DispatchWork => dispatchFallbackWork(m)

      case TSIndexMapping(mapping) =>
        ctx.log.debug("Received TS Index Mapping: {}", mapping.size)
        potentiallyComputePreClusters(Some(mapping), dists)

      case ApproxDistances(dists) =>
        ctx.log.debug(s"Received approximate distances", dists.n)
        potentiallyComputePreClusters(mapping, Some(dists))

      case PreClustersGenerated(preClusters) =>
        ctx.log.info("Starting pre-clustering strategy with {} preClusters ({} already processed), serving", preClusters.length, processed.size)
        stash.unstashAll(
          new PreClusteringStrategy(params, mapping.get.map(_.swap), preClusters, processed).start()
        )

      case ReportStatus =>
        ctx.log.info("[REPORT] Preparing, {} fallback work items already processed", processed.size)
        Behaviors.same
    }

    private def potentiallyComputePreClusters(
                                               mapping: Option[Map[TsId, Int]],
                                               dists: Option[PDist]
                                             ): Behavior[StrategyCommand] = {
      if mapping.isDefined && dists.isDefined then
        ctx.log.info("Received both approximate distances and mapping, computing pre clusters ({} already processed)", processed.size)
        val f = Future { computePreClusters(dists.get) }
        ctx.pipeToSelf(f) {
          case Success(preClusters) => PreClustersGenerated(preClusters)
          case Failure(e) => throw e
        }
      collecting(mapping, dists)
    }

    private def computePreClusters(dists: PDist): Array[Array[Int]] = {
      val hierarchy = computeHierarchy(dists, params.params.linkage)
      val preClasses = Math.sqrt(dists.n).toInt * 3
      val preLabels = CutTree(hierarchy, preClasses).zipWithIndex
      val clusters = Array.ofDim[Array[Int]](preClasses)
      for i <- 0 until preClasses do
        clusters(i) = preLabels.withFilter(_._1 == i).map(_._2)
      clusters
    }
  }
}

class PreClusteringStrategy private(params: InternalStrategyParameters,
                                    reverseMapping: Map[Int, StrategyProtocol.TsId],
                                    preClusters: Array[Array[Int]],
                                    processed: CompactPairwiseBitset
                                   ) extends Strategy(params) {
  import OrderedPreClusteringWorkGenerator.*
  import PreClusteringStrategy.*

  private val distMatrixAdapter = ctx.messageAdapter[DistanceMatrix](m => CurrentDistanceMatrix(m.distances))

  ctx.log.info("STARTING with intra cluster state")
  private val settings = Settings(ctx.system)
  private val n = reverseMapping.size
  private val m = n * (n - 1) / 2
  private val preClusterMedoids = Array.fill[Int](preClusters.length) {-1}
  { // initialize the singleton cluster medoids
    var i = 0
    while i < preClusters.length do
      if preClusters(i).length < 2 then
        preClusterMedoids(i) = preClusters(i).head
      i += 1
  }

  private var state: State = State.IntraCluster
  private var workGen: WorkGenerator[Int] = {
    val gen = new PreClusterIntraClusterGen(preClusters, n)
    if gen.hasNext then
      gen
    else
      nextState
  }

  override def start(): Behavior[StrategyCommand] = running()

  def running(): Behavior[StrategyCommand] = Behaviors.receiveMessage[StrategyCommand] {
    case AddTimeSeries(_) =>
      // ignore
      Behaviors.same

    case msg @ DispatchWork(worker, _, _) if state == State.Medoids && workGen.hasNext =>
      val (m1, m2) = workGen.next()
      val tsIds1 = preClusters.find(ids => ids.contains(m1)).get.map(reverseMapping)
      val tsIds2 = preClusters.find(ids => ids.contains(m2)).get.map(reverseMapping)
      if processed(m1, m2) then
        if tsIds1.length == 1 && tsIds2.length == 1 then
          ctx.log.info(
            "Medoid pair {}, {} ({} x {}) already processed and no other members in clusters, skipping",
              m1, m2, tsIds1.length, tsIds2.length
          )
          // requeue work request to fetch the next job for this worker!
          ctx.self ! msg
        else
          ctx.log.info(
            "Medoid pair {}, {} ({} x {}) already processed, but needed for broadcast, re-computing full-distance",
            m1, m2, tsIds1.length, tsIds2.length
          )
          worker ! WorkerProtocol.CheckMedoids(reverseMapping(m1), reverseMapping(m2), tsIds1, tsIds2, justBroadcast = true)
      else
        ctx.log.debug(
          "Dispatching medoids job {}, {} ({} x {}), remaining={}, Stash={}",
          m1, m2, tsIds1.length, tsIds2.length, workGen.remaining, stash.size
        )
        processed.add(m1, m2)
        worker ! WorkerProtocol.CheckMedoids(reverseMapping(m1), reverseMapping(m2), tsIds1, tsIds2)
      Behaviors.same

    case DispatchWork(worker, time, size) if workGen.hasNext =>
      val batchSize = nextBatchSize(time, size)
      val work = workGen
        .nextBatch(batchSize, ij => processed(ij._1, ij._2))
        .map{ (i, j) =>
          processed.add(i, j)
          val (iMapped, jMapped) = (reverseMapping(i), reverseMapping(j))
          if iMapped < jMapped then
            (iMapped, jMapped)
          else
            (jMapped, iMapped)
        }
      ctx.log.trace("Dispatching full job ({}) remaining={}, Stash={}", work.length, workGen.remaining, stash.size)
      worker ! WorkerProtocol.CheckFull(work)
      Behaviors.same

    case msg@DispatchWork(worker, _, _) if processed.size < m =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={}), changing state ({}/{} {}, {}/{} overall)", worker.path.name, stash.size, workGen.index, workGen.sizeTuples, state, processed.size, m)
      stash.stash(msg)
      if stash.size == settings.numberOfWorkers then
        workGen = nextState
      Behaviors.same

    case msg@DispatchWork(worker, _, _) =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={}), finished ({}/{} {}, {}/{} overall)", worker.path.name, stash.size, workGen.index, workGen.sizeTuples, state, processed.size, m)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      stash.stash(msg)
      Behaviors.same

    case CurrentDistanceMatrix(dists) =>
      ctx.log.info("Received current distance matrix (stash={})", stash.size)
      state match {
        case State.IntraCluster =>
          ctx.log.warn("Received distance matrix in state {}, ignoring", state)
          Behaviors.same

        case State.Medoids =>
          ctx.log.info("Computing medoids")
          for i <- preClusters.indices if preClusterMedoids(i) == -1 do
            preClusterMedoids(i) = computePreClusterMedoid(preClusters(i), dists)
          workGen = new PreClusterMedoidPairGenerator(preClusterMedoids)
          stash.unstashAll(Behaviors.same)

        case State.InterCluster =>
          ctx.log.info("Sorting pre-cluster pairs to create inter-cluster queue")
          val queue = createInterClusterQueue(preClusters, preClusterMedoids, dists)
          workGen = new PreClusterInterClusterGen(queue, preClusters, preClusterMedoids)
          stash.unstashAll(Behaviors.same)
      }

    case ReportStatus =>
      ctx.log.info(
        "[REPORT] {}, {}/{} work items in this state remaining, {}",
        state, workGen.remaining, workGen.sizeTuples, getBatchStats
      )
      Behaviors.same
  } receiveSignal {
    case (_, PostStop) =>
      ctx.log.info(
        "[REPORT] {}, {}/{} work items in this state remaining, {}",
        state, workGen.remaining, workGen.sizeTuples, getBatchStats
      )
      Behaviors.same
  }

  private def nextState: WorkGenerator[Int] = {
    state match {
      case State.IntraCluster =>
        state = State.Medoids
        ctx.log.info("SWITCHING to medoids state")
        params.clusterer ! GetCurrentDistanceMatrix(distMatrixAdapter)
        WorkGenerator.empty

      case State.Medoids =>
        state = State.InterCluster
        ctx.log.info("SWITCHING to inter cluster state")
        params.clusterer ! GetCurrentDistanceMatrix(distMatrixAdapter)
        WorkGenerator.empty

      case State.InterCluster =>
        throw new IllegalStateException(
          s"There is no other state after $state! Strategy should have been finished (${processed.size}/$m)."
        )
    }
  }
}
