package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.actors.{Communicator, TimeSeriesManager}
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.Status

import scala.annotation.tailrec
import scala.collection.immutable.Queue

object Strategy {
  trait Command
  case class DispatchWork(worker: ActorRef[Worker.Command]) extends Command
  case class AddTimeSeries(timeseriesIds: Seq[Long]) extends Command

  def fcfs(
            params: DendroTimeParams,
            tsManager: ActorRef[TimeSeriesManager.Command],
            coordinator: ActorRef[Coordinator.Command],
            clusterer: ActorRef[Clusterer.Command],
          ): Behavior[Command] = Behaviors.setup { ctx =>
    val settings = Settings(ctx.system)
    val stashSize = settings.numberOfWorkers * 5

    Behaviors.withStash(stashSize) { stash =>
      new Strategy(ctx, stash, coordinator, settings.numberOfWorkers).start()
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

// FCFS strategy
class Strategy(ctx: ActorContext[Strategy.Command],
               stash: StashBuffer[Strategy.Command],
               coordinator: ActorRef[Coordinator.Command],
               numberOfWorkers: Int
              ) {

  import Strategy.*

  def start(): Behavior[Command] = running(Seq.empty, Queue.empty)

  def running(tsIds: Seq[Long], workQueue: Queue[(Long, Long)]): Behavior[Command] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      val (newTsIds, newQueue) = addAllTimeSeries(tsIds, workQueue, timeseriesIds)
      ctx.log.debug("Added {} new time series to the queue(size={})", timeseriesIds.size, newQueue.size)
      if newQueue.nonEmpty then
        stash.unstashAll(running(newTsIds, newQueue))
      else
        running(newTsIds, newQueue)

    case DispatchWork(worker) if workQueue.nonEmpty =>
      val (work, newQueue) = workQueue.dequeue
      ctx.log.debug("Dispatching full job ({}) WorkQueue={}, Stash={}", work, newQueue.size, stash.size)
      worker ! Worker.CheckFull(work._1, work._2)
      running(tsIds, newQueue)

    case m: DispatchWork =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", m.worker, stash.size)
      if stash.isEmpty then
        coordinator ! Coordinator.FullOutOfWork
      else if stash.size + 1 == numberOfWorkers then
        coordinator ! Coordinator.FullStrategyFinished
      stash.stash(m)
      Behaviors.same
  }
}
