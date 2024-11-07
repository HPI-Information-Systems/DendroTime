package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.actors.TimeSeriesManager
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.{StrategyCommand, StrategyEvent}
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams

object StrategyFactory {
  case class StrategyParameters(
                                 dataset: Dataset,
                                 params: DendroTimeParams,
                                 tsManager: ActorRef[TimeSeriesManager.Command],
                                 clusterer: ActorRef[Clusterer.Command],
                               )

  def get(strategy: String): StrategyFactory = strategy.toLowerCase.strip().replace(" ", "-").replace("_", "-") match {
    case "fcfs" => FCFSStrategy
    case "shortest-ts" => ShortestTsStrategy
    case "approx-distance-ascending" => ApproxDistanceStrategy.Ascending
    case "approx-distance-descending" => ApproxDistanceStrategy.Descending
    case _ => throw new IllegalArgumentException(s"Unknown strategy: $strategy")
  }

  def apply(strategy: String, params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    get(strategy)(params, eventReceiver)
}

trait StrategyFactory {
  def apply(params: StrategyFactory.StrategyParameters,
            eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand]
}
