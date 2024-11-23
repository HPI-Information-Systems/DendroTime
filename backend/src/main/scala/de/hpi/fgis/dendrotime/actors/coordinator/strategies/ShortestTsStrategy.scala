package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.AdaptiveBatchingMixin
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.structures.WorkTupleGenerator

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, boundary}


object ShortestTsStrategy extends StrategyFactory {
  
  private case class TSLengthsResponse(lengths: Map[Long, Int]) extends StrategyCommand
  
  private case class QueueCreated(queue: Array[(Long, Long)]) extends StrategyCommand

  def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5
  
      Behaviors.withStash(stashSize) { stash =>
        new ShortestTsStrategy(ctx, stash, eventReceiver, params).start()
      }
    }

  private def createQueue(lengths: Map[Long, Int], processedWork: Set[(Long, Long)]): Array[(Long, Long)] = {
    val idLengths = lengths.iterator.toArray
    idLengths.sortInPlaceBy(_._2)
    val ids = idLengths.map(_._1)
    val builder = mutable.ArrayBuilder.make[(Long, Long)]
    for right <- 1 until ids.length do
      for left <- 0 until right do
        val idLeft = ids(left)
        val idRight = ids(right)
        val pair =
          if idLeft < idRight then (idLeft, idRight)
          else (idRight, idLeft)
        if !processedWork.contains(pair) then
          builder += (pair)
    builder.result()
  }
}

class ShortestTsStrategy private(ctx: ActorContext[StrategyCommand],
                                 stash: StashBuffer[StrategyCommand],
                                 eventReceiver: ActorRef[StrategyEvent],
                                 params: StrategyParameters
                                ) {

  import ShortestTsStrategy.*
  
  private val tsAdapter = ctx.messageAdapter[TsmProtocol.TSLengthsResponse](m => TSLengthsResponse(m.lengths))
  private val fallbackWorkGenerator = new WorkTupleGenerator
  // Executor for internal futures (CPU-heavy work)
  private given ExecutionContext = ctx.system.dispatchers.lookup(DispatcherSelector.blocking())

  def start(): Behavior[StrategyCommand] = {
    params.tsManager ! TsmProtocol.GetTSLengths(params.dataset.id, tsAdapter)
    collecting(Set.empty)
  }

  private def collecting(processedWork: Set[(Long, Long)]): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      fallbackWorkGenerator.addAll(timeseriesIds)
      if fallbackWorkGenerator.hasNext then
        stash.unstashAll(Behaviors.same)
      else
        Behaviors.same

    case TSLengthsResponse(lengths) =>
      val f = Future { createQueue(lengths, processedWork) }
      ctx.pipeToSelf(f){
        case Success(queue) => QueueCreated(queue)
        case Failure(e) => throw e
      }
      ctx.log.debug("Received lengths of {} time series, building work Queue", lengths.size)
      Behaviors.same

    case QueueCreated(queue) =>
      val newQueue = queue.filterNot(processedWork.contains)
      if newQueue.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      ctx.log.info("Received work queue of size {} ({} already processed), serving", newQueue.length, processedWork.size)
      stash.unstashAll(serving(newQueue, nextItem = 0))

    case m@DispatchWork(worker, _, _) =>
      if fallbackWorkGenerator.hasNext then
        val work = fallbackWorkGenerator.next()
        ctx.log.trace("Dispatching full job ({}) processedWork={}, Stash={}", work, processedWork.size, stash.size)
        worker ! WorkerProtocol.CheckFull(work._1, work._2)
        collecting(processedWork + work)
      else
        ctx.log.debug("Worker {} asked for work but there is none (stash={})", worker, stash.size)
        if stash.isEmpty then
          eventReceiver ! FullStrategyOutOfWork
        stash.stash(m)
        Behaviors.same
  }
  
  private def serving(workQueue: Array[(Long, Long)], nextItem: Int): Behavior[StrategyCommand] = Behaviors.receiveMessagePartial {
    case AddTimeSeries(_) =>
      // ignore
      Behaviors.same

    case DispatchWork(worker, _, _) if nextItem < workQueue.length =>
      val work = workQueue(nextItem)
      ctx.log.trace("Dispatching full job ({}) nextItem={}/{}, Stash={}", work, nextItem + 1, workQueue.length, stash.size)
      worker ! WorkerProtocol.CheckFull(work._1, work._2)
      serving(workQueue, nextItem + 1)

    case m @ DispatchWork(worker, _, _) =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      stash.stash(m)
      Behaviors.same
  }
}
