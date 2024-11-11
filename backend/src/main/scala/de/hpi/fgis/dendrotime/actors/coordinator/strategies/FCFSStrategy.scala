package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.worker.Worker

import scala.annotation.tailrec
import scala.collection.mutable

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
  private[strategies] def addAllTimeSeries(tsIds: mutable.ArrayBuffer[Long], workQueue: mutable.Queue[(Long, Long)], newIds: Seq[Long]): Unit =
    newIds.headOption match {
      case None =>
      case Some(newId) =>
        if tsIds.nonEmpty then
          workQueue.enqueueAll(tsIds.map((_, newId)))
        tsIds += newId
        addAllTimeSeries(tsIds, workQueue, newIds.tail)
    }
}

class FCFSStrategy private(ctx: ActorContext[StrategyCommand],
                           stash: StashBuffer[StrategyCommand],
                           eventReceiver: ActorRef[StrategyEvent],
                           numberOfWorkers: Int
                          ) {

  import FCFSStrategy.*

  private val workQueue = mutable.Queue.empty[(Long, Long)]
  private val tsIds = mutable.ArrayBuffer.empty[Long]

  def start(): Behavior[StrategyCommand] = running()

  private def running(): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      addAllTimeSeries(tsIds, workQueue, timeseriesIds)
      ctx.log.trace("Added {} new time series to the queue", timeseriesIds.size)
      if workQueue.nonEmpty then
        stash.unstashAll(Behaviors.same)
      else
        Behaviors.same

    case DispatchWork(worker) if workQueue.nonEmpty =>
      val work = workQueue.dequeue
      ctx.log.trace("Dispatching full job ({}), Stash={}", work, stash.size)
      worker ! Worker.CheckFull(work._1, work._2)
      Behaviors.same

    case m: DispatchWork =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", m.worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      if stash.size + 1 >= numberOfWorkers then
        eventReceiver ! FullStrategyFinished
      stash.stash(m)
      Behaviors.same
  }
}
