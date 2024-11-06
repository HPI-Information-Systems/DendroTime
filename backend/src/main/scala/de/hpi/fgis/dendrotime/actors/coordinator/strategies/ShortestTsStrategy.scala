package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.TimeSeriesManager
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.worker.Worker

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.collection.mutable.ArrayBuffer
import scala.util.boundary


object ShortestTsStrategy extends StrategyFactory {
  
  private case class TSLengthsResponse(lengths: Map[Long, Int]) extends StrategyCommand

  def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5
  
      Behaviors.withStash(stashSize) { stash =>
        new ShortestTsStrategy(ctx, stash, eventReceiver, settings.numberOfWorkers, params).start()
      }
    }

//  @tailrec
//  private def createQueue(tsIds: Iterable[Long], processedWork: Set[(Long, Long)], queue: Queue[(Long, Long)]): Queue[(Long, Long)] =
//    tsIds.headOption match {
//      case None => Queue.empty
//      case Some(newId) =>
//        val newQueue =
//          if tsIds.nonEmpty then
//            queue.enqueueAll(tsIds.map((_, newId)).filterNot(processedWork.contains))
//          else
//            queue
//        createQueue(tsIds.tail, processedWork, newQueue)
//    }

  private def createQueue(tsIds: Array[Long], processedWork: Set[(Long, Long)]): Queue[(Long, Long)] = {
    val builder = Queue.newBuilder[(Long, Long)]
    for right <- 1 until tsIds.length do
      for left <- 0 until right do
        val idLeft = tsIds(left)
        val idRight = tsIds(right)
        if !processedWork.contains((idLeft, idRight)) then
          builder += ((idLeft, idRight))
    builder.result()
  }
}

class ShortestTsStrategy(ctx: ActorContext[StrategyCommand],
                         stash: StashBuffer[StrategyCommand],
                         eventReceiver: ActorRef[StrategyEvent],
                         numberOfWorkers: Int,
                         params: StrategyParameters
                        ) {

  import ShortestTsStrategy.*
  
  private val tsAdapter = ctx.messageAdapter[TimeSeriesManager.TSLengthsResponse](m => TSLengthsResponse(m.lengths))
  private val tsIds = ArrayBuffer.empty[Long]

  def start(): Behavior[StrategyCommand] = {
    params.tsManager ! TimeSeriesManager.GetTSLengths(params.dataset.id, tsAdapter)
    collecting(Set.empty)
  }

  private def collecting(processedWork: Set[(Long, Long)]): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      tsIds ++= timeseriesIds
      ctx.log.info("Added {} new time series ", timeseriesIds.size)
      if tsIds.size >= 2 then
        stash.unstashAll(Behaviors.same)
      else
        Behaviors.same

    case TSLengthsResponse(lengths) =>
      ctx.log.info("Received lengths of {} time series, building work Queue", lengths.size)
      val ids = lengths.keys.toArray
      ids.sortInPlaceBy(lengths.apply)
      val queue = createQueue(ids, processedWork)
      if queue.isEmpty then
        eventReceiver ! FullStrategyFinished

      ctx.log.info("Queue size={}", queue.size)
      serving(queue)

    case m @ DispatchWork(worker) =>
      nextWork(processedWork) match {
        case Some(work) =>
          ctx.log.debug("Dispatching full job ({}) processedWork={}, Stash={}", work, processedWork.size, stash.size)
          worker ! Worker.CheckFull(work._1, work._2)
          val newProcessedWork = processedWork + work
          collecting(newProcessedWork)
        case None =>
          ctx.log.info("Worker {} asked for work but there is none (stash={})", worker, stash.size)
          stash.stash(m)
          Behaviors.same
      }
  }
  
  private def serving(workQueue: Queue[(Long, Long)]): Behavior[StrategyCommand] = Behaviors.receiveMessagePartial {
    case AddTimeSeries(_) =>
      // ignore
      Behaviors.same

    case DispatchWork(worker) if workQueue.nonEmpty =>
      val (work, newQueue) = workQueue.dequeue
      ctx.log.debug("Dispatching full job ({}) queue={}, Stash={}", work, newQueue.size, stash.size)
      worker ! Worker.CheckFull(work._1, work._2)
      serving(newQueue)

    case m @ DispatchWork(worker) =>
      ctx.log.info("Worker {} asked for work but there is none (stash={})", worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      else if stash.size + 1 == numberOfWorkers then
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
}
