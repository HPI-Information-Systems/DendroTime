package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props}
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.{StrategyCommand, StrategyEvent}
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams

object StrategyFactory {
  case class StrategyParameters(
                                 dataset: Dataset,
                                 params: DendroTimeParams,
                                 tsManager: ActorRef[TsmProtocol.Command],
                                 clusterer: ActorRef[ClustererProtocol.Command],
                               )

  def get(strategy: String): StrategyFactory = strategy.toLowerCase.strip().replace(" ", "-").replace("_", "-") match {
    case "fcfs" => FCFSStrategy
    case "shortest-ts" => ShortestTsStrategy
    case "approx-distance-ascending" => ApproxDistanceStrategy.Ascending
    case "approx-distance-descending" => ApproxDistanceStrategy.Descending
    case _ => throw new IllegalArgumentException(
      s"Unknown strategy: $strategy, use one of: fcfs, shortest-ts, approx-distance-ascending, approx-distance-descending"
    )
  }

  def apply(strategy: String, params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    get(strategy)(params, eventReceiver)

  def props: Props = DispatcherSelector.fromConfig("dendrotime.coordinator-pinned-dispatcher")
}

trait StrategyFactory {
  def apply(params: StrategyFactory.StrategyParameters,
            eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand]
}
