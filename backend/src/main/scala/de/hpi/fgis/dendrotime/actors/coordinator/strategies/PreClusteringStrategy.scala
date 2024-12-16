package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol.{DistanceMatrix, RegisterApproxDistMatrixReceiver}
import de.hpi.fgis.dendrotime.actors.coordinator.AdaptiveBatchingMixin
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol.{GetTSIndexMapping, TSIndexMappingResponse}
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, computeHierarchy}
import de.hpi.fgis.dendrotime.structures.strategies.{GrowableFCFSWorkGenerator, OrderedPreClusteringWorkGenerator, WorkGenerator}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object PreClusteringStrategy extends StrategyFactory {

  private case class TSIndexMapping(mapping: Map[Long, Int]) extends StrategyCommand
  private case class ApproxDisances(dists: PDist) extends StrategyCommand
  private case class PreClustersGenerated(preClusters: Array[Array[Int]]) extends StrategyCommand

  def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5

      Behaviors.withStash(stashSize) { stash =>
        new PreClusteringStrategy(ctx, stash, eventReceiver, params).start()
      }
    }
}

class PreClusteringStrategy private(ctx: ActorContext[StrategyCommand],
                                    stash: StashBuffer[StrategyCommand],
                                    eventReceiver: ActorRef[StrategyEvent],
                                    params: StrategyParameters
                                   ) extends AdaptiveBatchingMixin(ctx.system) {
  import PreClusteringStrategy.*

  private val fallbackWorkGenerator = GrowableFCFSWorkGenerator.empty[Long]
  private val tsIndexMappingAdapter = ctx.messageAdapter[TSIndexMappingResponse](m => TSIndexMapping(m.mapping))
  private val approxDistancesAdapter = ctx.messageAdapter[DistanceMatrix](m => ApproxDisances(m.distances))
  private val preClusteringHandler = new OrderedPreClusteringWorkGenerator.StatusUpdateListener {
    override def createInterClusterQueue(preClusters: Array[Array[Int]], preClusterMedoids: Array[Int]): Array[(Int, Int)] = ???
    override def computePreClusterMedoid(ids: Array[Int]): Int = ???
  }

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

    case ApproxDisances(dists) =>
      ctx.log.debug(s"Received approximate distances", dists.n)
      potentiallyComputePreClusters(processedWork, mapping, Some(dists))

    case PreClustersGenerated(preClusters) =>
      val queue = OrderedPreClusteringWorkGenerator(mapping.get, preClusters, preClusteringHandler)
      ctx.log.info("Starting generation of  work queue of size {} ({} already processed), serving", queue.sizeTuples, processedWork.size)
      stash.unstashAll(serving(queue, processedWork))

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

  private def serving(workGen: WorkGenerator[Long], processedWork: Set[(Long, Long)]): Behavior[StrategyCommand] = Behaviors.receiveMessagePartial {
    case AddTimeSeries(_) =>
      // ignore
      Behaviors.same

    case DispatchWork(worker, time, size) if workGen.hasNext =>
      val batchSize = nextBatchSize(time, size)
      val work = workGen.nextBatch(batchSize, processedWork)
      ctx.log.trace("Dispatching full job ({}) remaining={}, Stash={}", work.length, workGen.remaining, stash.size)
      worker ! WorkerProtocol.CheckFull(work)
      Behaviors.same

    case m@DispatchWork(worker, _, _) =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      stash.stash(m)
      Behaviors.same

    case ReportStatus =>
      ctx.log.info(
        "[REPORT] Serving, {}/{} work items remaining, {}",
        workGen.remaining, workGen.sizeTuples, getBatchStats
      )
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
    val clusters = mapping.values.toArray.groupBy(id => preLabels(mapping(id)))
    clusters.toArray.sortBy(_._1).map(_._2)
  }
}
