package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.TimeSeriesManager
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams

import scala.annotation.tailrec
import scala.collection.immutable.Queue

object FCFSStrategy extends StrategyFactory {
  def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5
  
      Behaviors.withStash(stashSize) { stash =>
        new FCFSStrategy(ctx, stash, eventReceiver, settings.numberOfWorkers).start()
      }
    }

  @tailrec
  private[strategies] def addAllTimeSeries(tsIds: Seq[Long], workQueue: Queue[(Long, Long)], newIds: Seq[Long]): (Seq[Long], Queue[(Long, Long)]) =
    newIds.headOption match {
      case None => (tsIds, workQueue)
      case Some(newId) =>
        val newQueue =
          if tsIds.nonEmpty then
            workQueue.enqueueAll(tsIds.map((_, newId)))
          else
            workQueue
        addAllTimeSeries(tsIds :+ newId, newQueue, newIds.tail)
    }
}

class FCFSStrategy(ctx: ActorContext[StrategyCommand],
                   stash: StashBuffer[StrategyCommand],
                   eventReceiver: ActorRef[StrategyEvent],
                   numberOfWorkers: Int
                  ) {

  import FCFSStrategy.*

  def start(): Behavior[StrategyCommand] = running(Seq.empty, Queue.empty)

  private def running(tsIds: Seq[Long], workQueue: Queue[(Long, Long)]): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      val (newTsIds, newQueue) = addAllTimeSeries(tsIds, workQueue, timeseriesIds)
      ctx.log.trace("Added {} new time series to the queue(size={})", timeseriesIds.size, newQueue.size)
      if newQueue.nonEmpty then
        stash.unstashAll(running(newTsIds, newQueue))
      else
        running(newTsIds, newQueue)

    case DispatchWork(worker) if workQueue.nonEmpty =>
      val (work, newQueue) = workQueue.dequeue
      ctx.log.trace("Dispatching full job ({}) WorkQueue={}, Stash={}", work, newQueue.size, stash.size)
      worker ! Worker.CheckFull(work._1, work._2)
      running(tsIds, newQueue)

    case m: DispatchWork =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", m.worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      else if stash.size + 1 == numberOfWorkers then
        eventReceiver ! FullStrategyFinished
      stash.stash(m)
      Behaviors.same
  }
}
