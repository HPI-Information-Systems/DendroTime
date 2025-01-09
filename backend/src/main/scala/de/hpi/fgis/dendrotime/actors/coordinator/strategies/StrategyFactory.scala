package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyParameters.InternalStrategyParameters
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.{AddTimeSeries, ReportStatus, StrategyCommand, StrategyEvent}
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams

object StrategyFactory {

  def get(strategy: String): StrategyFactory = strategy.toLowerCase.strip().replace(" ", "-").replace("_", "-") match {
    case "fcfs" => FCFSStrategy
    case "shortest-ts" => ShortestTsStrategy
    case "approx-distance-ascending" => ApproxDistanceStrategy.Ascending
    case "approx-distance-descending" => ApproxDistanceStrategy.Descending
    case "pre-clustering" => PreClusteringStrategy
    case _ => throw new IllegalArgumentException(
      s"Unknown strategy: $strategy, use one of: fcfs, shortest-ts, approx-distance-ascending, approx-distance-descending, pre-clustering"
    )
  }

  def apply(strategy: String, params: StrategyParameters, eventReceiver: ActorRef[StrategyEvent]): Behavior[StrategyCommand] =
    Behaviors.setup { ctx =>
      val settings = Settings(ctx.system)
      val stashSize = settings.numberOfWorkers * 5
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(ReportStatus, Settings(ctx.system).reportingInterval)
        Behaviors.withStash(stashSize) { stash =>
          Behaviors.receiveMessage {
            case AddTimeSeries(timeseriesIds) =>
              stash.unstashAll(
                get(strategy)(params.toInternal(eventReceiver, ctx, stash, timeseriesIds))
              )
            case m =>
              stash.stash(m)
              Behaviors.same
          }
        }
      }
    }

  def props: Props = DispatcherSelector.fromConfig("dendrotime.coordinator-pinned-dispatcher")
}

trait StrategyFactory {
  def apply(params: InternalStrategyParameters): Behavior[StrategyCommand]
}
