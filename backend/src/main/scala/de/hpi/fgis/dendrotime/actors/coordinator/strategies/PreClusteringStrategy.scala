package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, PostStop}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol.{DistanceMatrix, GetCurrentDistanceMatrix, RegisterApproxDistMatrixReceiver}
import de.hpi.fgis.dendrotime.actors.coordinator.AdaptiveBatchingMixin
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol.{GetTSIndexMapping, TSIndexMappingResponse}
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, computeHierarchy}
import de.hpi.fgis.dendrotime.structures.strategies.{GrowableFCFSWorkGenerator, OrderedPreClusteringWorkGenerator, WorkGenerator}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Using}

object PreClusteringStrategy extends StrategyFactory {

  private case class TSIndexMapping(mapping: Map[Long, Int]) extends StrategyCommand
  private case class ApproxDistances(dists: PDist) extends StrategyCommand
  private case class CurrentDistanceMatrix(dists: PDist) extends StrategyCommand
  private case class PreClustersGenerated(preClusters: Array[Array[Int]]) extends StrategyCommand

  def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5

      Behaviors.withStash(stashSize) { stash =>
        new Initializer(ctx, stash, eventReceiver, params).start()
      }
    }

  private class Initializer(ctx: ActorContext[StrategyCommand],
                            stash: StashBuffer[StrategyCommand],
                            eventReceiver: ActorRef[StrategyEvent],
                            params: StrategyParameters) extends AdaptiveBatchingMixin(ctx.system) {

    private val fallbackWorkGenerator = GrowableFCFSWorkGenerator.empty[Long]
    private val tsIndexMappingAdapter = ctx.messageAdapter[TSIndexMappingResponse](m => TSIndexMapping(m.mapping))
    private val approxDistancesAdapter = ctx.messageAdapter[DistanceMatrix](m => ApproxDistances(m.distances))

    // Executor for internal futures (CPU-heavy work)
    private given ExecutionContext = ctx.system.dispatchers.lookup(DispatcherSelector.blocking())

    def start(): Behavior[StrategyCommand] = {
      params.tsManager ! GetTSIndexMapping(params.dataset.id, tsIndexMappingAdapter)
      params.clusterer ! RegisterApproxDistMatrixReceiver(approxDistancesAdapter)
      collecting(Set.empty, None, None)
    }

    private def collecting(processedWork: Set[(Long, Long)], mapping: Option[Map[Long, Int]], dists: Option[PDist]): Behavior[StrategyCommand] = Behaviors.receiveMessage {
      case AddTimeSeries(timeseriesIds) =>
        fallbackWorkGenerator.addAll(timeseriesIds.sorted)
        if fallbackWorkGenerator.hasNext then
          stash.unstashAll(Behaviors.same)
        else
          Behaviors.same

      case TSIndexMapping(mapping) =>
        ctx.log.debug("Received TS Index Mapping: {}", mapping.size)
        potentiallyComputePreClusters(processedWork, Some(mapping), dists)

      case ApproxDistances(dists) =>
        ctx.log.debug(s"Received approximate distances", dists.n)
        potentiallyComputePreClusters(processedWork, mapping, Some(dists))

      case PreClustersGenerated(preClusters) =>
        ctx.log.info("Starting pre-clustering strategy with {} preClusters ({} already processed), serving", preClusters.length, processedWork.size)
        stash.unstashAll(startPreClusterer(processedWork, mapping.get, preClusters))

      case m@DispatchWork(worker, time, size) =>
        if fallbackWorkGenerator.hasNext then
          val batchSize = Math.max(nextBatchSize(time, size), 16)
          val work = fallbackWorkGenerator.nextBatch(batchSize)
          ctx.log.trace("Dispatching full job ({}) processedWork={}, Stash={}", work.length, processedWork.size, stash.size)
          worker ! WorkerProtocol.CheckFull(work)
          collecting(processedWork ++ work, mapping, dists)
        else
          ctx.log.debug("Worker {} asked for work but there is none (stash={})", worker, stash.size)
          if stash.isEmpty then
            eventReceiver ! FullStrategyOutOfWork
          stash.stash(m)
          Behaviors.same

      case ReportStatus =>
        ctx.log.info("[REPORT] Preparing, {} fallback work items already processed", processedWork.size)
        Behaviors.same
    }

    private def potentiallyComputePreClusters(processedWork: Set[(Long, Long)], mapping: Option[Map[Long, Int]], dists: Option[PDist]): Behavior[StrategyCommand] = {
      (mapping, dists) match {
        case (Some(m), Some(d)) =>
          ctx.log.info("Received both approximate distances and mapping, computing pre clusters ({} already processed)", processedWork.size)
          val f = Future {
            computePreClusters(d, m)
          }
          ctx.pipeToSelf(f) {
            case Success(preClusters) => PreClustersGenerated(preClusters)
            case Failure(e) => throw e
          }
        case _ =>
      }
      collecting(processedWork, mapping, dists)
    }

    private def computePreClusters(dists: PDist, mapping: Map[Long, Int]): Array[Array[Int]] = {
      val hierarchy = computeHierarchy(dists, params.params.linkage)
      val preClasses = Math.sqrt(dists.n).toInt * 3
      val preLabels = CutTree(hierarchy, preClasses)
      val clusters = mapping.values.toArray.groupBy(id => preLabels(id))
      clusters.toArray.sortBy(_._1).map(_._2)
    }

    private def startPreClusterer(processedWork: Set[(Long, Long)], mapping: Map[Long, Int], preClusters: Array[Array[Int]]): Behavior[StrategyCommand] = {
      val n = mapping.size
      val processed = new mutable.BitSet(n * (n - 1) / 2)
      val it = processedWork.iterator
      while it.hasNext do
        val (i, j) = it.next()
        processed.addOne(PDist.index(mapping(i), mapping(j), n))

      new PreClusteringStrategy(
        ctx, stash, eventReceiver, params, processed, mapping, preClusters
      ).running()
    }
  }
}

class PreClusteringStrategy private(ctx: ActorContext[StrategyCommand],
                                    stash: StashBuffer[StrategyCommand],
                                    eventReceiver: ActorRef[StrategyEvent],
                                    params: StrategyParameters,
                                    processed: mutable.BitSet,
                                    mapping: Map[Long, Int],
                                    preClusters: Array[Array[Int]]
                                   ) extends AdaptiveBatchingMixin(ctx.system) {
  import PreClusteringStrategy.*
  import OrderedPreClusteringWorkGenerator.*

  private val distMatrixAdapter = ctx.messageAdapter[DistanceMatrix](m => CurrentDistanceMatrix(m.distances))

  ctx.log.info("STARTING with intra cluster state")
  private val settings = Settings(ctx.system)
  private val reverseMapping: Map[Int, Long] = mapping.map(_.swap)
  private val n = mapping.size
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

  def running(): Behavior[StrategyCommand] = Behaviors.receiveMessage[StrategyCommand] {
    case AddTimeSeries(_) =>
      // ignore
      Behaviors.same

    case DispatchWork(worker, _, _) if state == State.Medoids && workGen.hasNext =>
      val (m1, m2) = workGen.next()
      if processed.contains(index(m1, m2)) then
        ctx.log.debug("Medoid pair ({}, {}) already processed, skipping", m1, m2)
      else
        val tsIds1 = preClusters.find(ids => ids.contains(m1)).get.map(reverseMapping)
        val tsIds2 = preClusters.find(ids => ids.contains(m2)).get.map(reverseMapping)
        ctx.log.debug(
          "Dispatching medoids job {}, {} ({} x {}), remaining={}, Stash={}",
          m1, m2, tsIds1.length, tsIds2.length, workGen.remaining, stash.size
        )
        processed.add(index(m1, m2))
        worker ! WorkerProtocol.CheckMedoids(reverseMapping(m1), reverseMapping(m2), tsIds1, tsIds2)
      Behaviors.same

    case DispatchWork(worker, time, size) if workGen.hasNext =>
      val batchSize = nextBatchSize(time, size)
      val work = workGen
        .nextBatch(batchSize) //, ij => processed.contains(index(ij._1, ij._2)))
        .flatMap{ case (i, j) =>
          if processed.contains(index(i, j)) then
            None
          else
            processed.add(index(i, j))
            val (iMapped, jMapped) = (reverseMapping(i), reverseMapping(j))
            if iMapped < jMapped then
              Some((iMapped, jMapped))
            else
              Some((jMapped, iMapped))
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
  }

  private def index(i: Int, j: Int): Int =
    if i < j then
      PDist.index(i, j, n)
    else
      PDist.index(j, i, n)


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
