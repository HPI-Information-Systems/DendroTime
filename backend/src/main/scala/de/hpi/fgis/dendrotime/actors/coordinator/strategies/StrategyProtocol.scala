package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.ActorRef
import de.hpi.fgis.dendrotime.actors.worker.{Worker, WorkerProtocol}

object StrategyProtocol {

  type TsId = Int

  trait StrategyCommand
  case class AddTimeSeries(timeseriesIds: Seq[TsId]) extends StrategyCommand
  case class DispatchWork(worker: ActorRef[WorkerProtocol.Command],
                          lastJobDuration: Long = 0,
                          lastBatchSize: Int = 1) extends StrategyCommand
  private[coordinator] case object ReportStatus extends StrategyCommand

  trait StrategyEvent
  case object FullStrategyOutOfWork extends StrategyEvent
}
