package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.ActorRef
import de.hpi.fgis.dendrotime.actors.worker.{Worker, WorkerProtocol}

import scala.collection.IndexedSeq


object StrategyProtocol {

  trait StrategyCommand

  case class AddTimeSeries(tsIds: IndexedSeq[Int]) extends StrategyCommand

  case class DispatchWork(worker: ActorRef[WorkerProtocol.Command],
                          lastJobDuration: Long = 0,
                          lastBatchSize: Int = 1) extends StrategyCommand

  private[coordinator] case object ReportStatus extends StrategyCommand

  trait StrategyEvent

  case object FullStrategyOutOfWork extends StrategyEvent
}
