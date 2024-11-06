package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.ActorRef
import de.hpi.fgis.dendrotime.actors.worker.Worker

object StrategyProtocol {

  trait StrategyCommand
  case class AddTimeSeries(timeseriesIds: Seq[Long]) extends StrategyCommand
  case class DispatchWork(worker: ActorRef[Worker.Command]) extends StrategyCommand

  trait StrategyEvent
  case object FullStrategyOutOfWork extends StrategyEvent
  case object FullStrategyFinished extends StrategyEvent
}
