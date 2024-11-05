package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.Communicator
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.Strategy.*
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.model.StateModel.Status

import scala.collection.immutable.Queue

object ApproxFCFSStrategy {
  def apply(
             delegate: ActorRef[Command],
             coordinator: ActorRef[Coordinator.Command],
             communicator: ActorRef[Communicator.Command]
           ): Behavior[Command] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5

      Behaviors.withStash(stashSize) { stash =>
        new ApproxFCFSStrategy(ctx, stash, delegate, coordinator, communicator, settings.numberOfWorkers).start()
      }
    }
}

class ApproxFCFSStrategy(
                          ctx: ActorContext[Command],
                          stash: StashBuffer[Command],
                          delegate: ActorRef[Command],
                          coordinator: ActorRef[Coordinator.Command],
                          communicator: ActorRef[Communicator.Command],
                          numberOfWorkers: Int
                        ) {

  def start(): Behavior[Command] = running(Seq.empty, Queue.empty, 0)

  def running(tsIds: Seq[Long], workQueue: Queue[(Long, Long)], idleWorkers: Int): Behavior[Command] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      val (newTsIds, newQueue) = addAllTimeSeries(tsIds, workQueue, timeseriesIds)
      ctx.log.debug("Added {} new time series to the queue", timeseriesIds.size)
      if newQueue.nonEmpty then
        stash.unstashAll(running(newTsIds, newQueue, idleWorkers))
      else
        running(newTsIds, newQueue, idleWorkers)

    case DispatchWork(worker) if workQueue.nonEmpty =>
      val (work, newQueue) = workQueue.dequeue
      ctx.log.debug("Dispatching approx job ({}) ApproxQueue={}", work, newQueue.size)
      worker ! Worker.CheckApproximate(work._1, work._2)
      Strategy.sendProgressUpdate(communicator, Status.Approximating, newQueue.size, tsIds.size)
      running(tsIds, newQueue, 0)

    case DispatchWork(worker) if workQueue.isEmpty && tsIds.nonEmpty =>
      ctx.log.info("Approx queue ran out of work, delegating to full strategy")
      delegate ! DispatchWork(worker)
      if idleWorkers == 0 then
        coordinator ! Coordinator.ApproxOutOfWork
      else if idleWorkers + 1 == numberOfWorkers then
        coordinator ! Coordinator.ApproxStrategyFinished
      running(tsIds, workQueue, idleWorkers + 1)

    case m: DispatchWork =>
      ctx.log.debug("Worker {} asked for work but there is none, stashing", m.worker)
      stash.stash(m)
      running(tsIds, workQueue, idleWorkers + 1)
  }
}
