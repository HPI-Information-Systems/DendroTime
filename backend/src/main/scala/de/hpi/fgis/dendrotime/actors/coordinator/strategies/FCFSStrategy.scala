package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyFactory.StrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.structures.strategies.GrowableFCFSWorkGenerator

object FCFSStrategy extends StrategyFactory {
  def apply(params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5

      Behaviors.withStash(stashSize) { stash =>
        new FCFSStrategy(ctx, stash, eventReceiver).start()
      }
    }
}

class FCFSStrategy private(ctx: ActorContext[StrategyCommand],
                           stash: StashBuffer[StrategyCommand],
                           eventReceiver: ActorRef[StrategyEvent]
                          ) {

  private val workGenerator = GrowableFCFSWorkGenerator.empty[Long]

  def start(): Behavior[StrategyCommand] = running()

  private def running(): Behavior[StrategyCommand] = Behaviors.receiveMessage {
    case AddTimeSeries(timeseriesIds) =>
      workGenerator.addAll(timeseriesIds)
      if workGenerator.hasNext then
        stash.unstashAll(Behaviors.same)
      else
        Behaviors.same

    case DispatchWork(worker) if workGenerator.hasNext =>
      val work = workGenerator.next()
      ctx.log.trace("Dispatching full job ({}), Stash={}", work, stash.size)
      worker ! Worker.CheckFull(work._1, work._2)
      Behaviors.same

    case m: DispatchWork =>
      ctx.log.debug("Worker {} asked for work but there is none (stash={})", m.worker, stash.size)
      if stash.isEmpty then
        eventReceiver ! FullStrategyOutOfWork
      stash.stash(m)
      Behaviors.same
  }
}
