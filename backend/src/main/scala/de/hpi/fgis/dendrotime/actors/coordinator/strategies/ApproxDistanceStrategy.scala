package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.TimeSeriesManager.{GetTSIndexMapping, TSIndexMappingResponse}
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer.{ApproxDistanceMatrix, RegisterApproxDistMatrixReceiver}
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.clustering.PDist

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.boundary


object ApproxDistanceStrategy {

  private enum Direction {
    case Ascending
    case Descending
  }

  private case class TSIndexMapping(mapping: Map[Long, Int]) extends StrategyCommand

  private case class ApproxDistances(dists: PDist) extends StrategyCommand

  object Ascending extends StrategyFactory {
    def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
      start(params, eventReceiver, Direction.Ascending)
  }

  object Descending extends StrategyFactory {
    def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
      start(params, eventReceiver, Direction.Descending)
  }

  private def start(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent], direction: Direction): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5

      Behaviors.withStash(stashSize) { stash =>
        new ApproxDistanceStrategy(ctx, stash, eventReceiver, settings.numberOfWorkers, params, direction).start()
      }
    }

  private def createQueue(mapping: Map[Long, Int], processedWork: Set[(Long, Long)]): Array[(Long, Long)] = {
    val ids = mapping.keys.toArray
    val builder = mutable.ArrayBuilder.make[(Long, Long)]
    for i <- 0 until ids.length - 1 do
      for j <- i + 1 until ids.length do
        val idLeft = ids(i)
        val idRight = ids(j)
        if !processedWork.contains((idLeft, idRight)) && !processedWork.contains((idRight, idLeft)) then
          builder += ((idLeft, idRight))
    builder.result()
  }
}

class ApproxDistanceStrategy private(ctx: ActorContext[StrategyCommand],
                                     stash: StashBuffer[StrategyCommand],
                                     eventReceiver: ActorRef[StrategyEvent],
                                     numberOfWorkers: Int,
                                     params: StrategyParameters,
                                     direction: ApproxDistanceStrategy.Direction
                                    ) {

  import ApproxDistanceStrategy.*

  private val tsIds = ArrayBuffer.empty[Long]
  private val tsIndexMappingAdapter = ctx.messageAdapter[TSIndexMappingResponse](m => TSIndexMapping(m.mapping))
  private val approxDistancesAdapter = ctx.messageAdapter[ApproxDistanceMatrix](m => ApproxDistances(m.distances))

  def start(): Behavior[StrategyCommand] = {
    params.tsManager ! GetTSIndexMapping(params.dataset.id, tsIndexMappingAdapter)
    params.clusterer ! RegisterApproxDistMatrixReceiver(approxDistancesAdapter)
    collecting(Set.empty, None, None)
  }

  private def collecting(processedWork: Set[(Long, Long)], mapping: Option[Map[Long, Int]], dists: Option[PDist]): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      tsIds ++= timeseriesIds
      ctx.log.trace("Added {} new time series ", timeseriesIds.size)
      if tsIds.size >= 2 then
        stash.unstashAll(Behaviors.same)
      else
        Behaviors.same

    case TSIndexMapping(mapping) =>
      ctx.log.debug("Received TS Index Mapping")
      potentiallyChangeState(processedWork, Some(mapping), dists)

    case ApproxDistances(dists) =>
      ctx.log.debug("Received approximate distances")
      potentiallyChangeState(processedWork, mapping, Some(dists))

    case m@DispatchWork(worker) =>
      nextWork(processedWork) match {
        case Some(work) =>
          ctx.log.trace("Dispatching full job ({}) processedWork={}, Stash={}", work, processedWork.size, stash.size)
          worker ! Worker.CheckFull(work._1, work._2)
          val newProcessedWork = processedWork + work
          collecting(newProcessedWork, mapping, dists)
        case None =>
          ctx.log.debug("Worker {} asked for work but there is none (stash={})", worker, stash.size)
          if stash.isEmpty then
            eventReceiver ! FullStrategyOutOfWork
          if stash.size + 1 >= numberOfWorkers then
            eventReceiver ! FullStrategyFinished
          stash.stash(m)
          Behaviors.same
      }
  }

  private def serving(workQueue: Array[(Long, Long)], nextItem: Int): Behavior[StrategyCommand] = Behaviors.receiveMessagePartial {
    case AddTimeSeries(_) =>
      // ignore
      Behaviors.same

    case DispatchWork(worker) if nextItem < workQueue.length =>
      val work =
        if direction == Direction.Ascending then workQueue(nextItem)
        else workQueue(workQueue.length - nextItem - 1)
      ctx.log.trace("Dispatching full job ({}) nextItem={}/{}, Stash={}", work, nextItem + 1, workQueue.length, stash.size)
      worker ! Worker.CheckFull(work._1, work._2)
      serving(workQueue, nextItem + 1)

    case m@DispatchWork(worker) =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      if stash.size + 1 >= numberOfWorkers then
        eventReceiver ! FullStrategyFinished
      stash.stash(m)
      Behaviors.same
  }

  private def nextWork(processed: Set[(Long, Long)]): Option[(Long, Long)] = {
    boundary {
      for i <- 0 until tsIds.size - 1 do
        for j <- i + 1 until tsIds.size do
          val work = (tsIds(i), tsIds(j))
          if !processed.contains(work) then
            boundary.break(Some(work))
      None
    }
  }

  private def potentiallyChangeState(processedWork: Set[(Long, Long)], mapping: Option[Map[Long, Int]], dists: Option[PDist]): Behavior[StrategyCommand] = {
    (mapping, dists) match {
      case (Some(m), Some(d)) =>
        val work = createQueue(m, processedWork)
        work.sortInPlaceBy((l, r) => d(m(l), m(r)))
        ctx.log.info("Received both approximate distances and mapping, building work Queue of size {}", work.length)
        if work.isEmpty then
          eventReceiver ! FullStrategyFinished
        serving(work, nextItem = 0)

      case _ =>
        collecting(processedWork, mapping, dists)
    }
  }
}
