package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyParameters.InternalStrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.*
import de.hpi.fgis.dendrotime.actors.worker.WorkerProtocol
import de.hpi.fgis.dendrotime.structures.strategies.GrowableFCFSWorkGenerator

object FCFSStrategy extends StrategyFactory {
  override def apply(params: InternalStrategyParameters): Behavior[StrategyCommand] =
    new FCFSStrategy(params).start()
}

class FCFSStrategy private(params: InternalStrategyParameters) extends Strategy(params) {

  override def start(): Behavior[StrategyCommand] = running()

  private def running(): Behavior[StrategyCommand] = Behaviors.receiveMessage[StrategyCommand] {
    case AddTimeSeries(_) => Behaviors.same // ignore

    case m: DispatchWork => dispatchFallbackWork(m)

    case ReportStatus =>
      ctx.log.info(
        "[REPORT] Serving, {}/{} work items remaining, {}",
        fallbackWorkGenerator.remaining, fallbackWorkGenerator.sizeTuples, getBatchStats
      )
      Behaviors.same

  } receiveSignal {
    case (_, PostStop) =>
      ctx.log.info(
        "[REPORT] Serving, {}/{} work items remaining, {}",
        fallbackWorkGenerator.remaining, fallbackWorkGenerator.sizeTuples, getBatchStats
      )
      Behaviors.same
  }
}
