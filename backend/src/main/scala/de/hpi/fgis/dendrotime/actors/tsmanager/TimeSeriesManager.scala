package de.hpi.fgis.dendrotime.actors.tsmanager

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.DatasetLoader
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.{LabeledTimeSeries, TimeSeries}

import scala.collection.AbstractIterator
import scala.collection.immutable.{HashMap, Set}


object TimeSeriesManager {
  def apply(): Behavior[TsmProtocol.Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(TsmProtocol.ReportStatus, Settings(ctx.system).reportingInterval)
      Behaviors.withStash(100) { stash =>
        new TimeSeriesManager(ctx, stash).start()
      }
    }
  }

  private final case class AddReceiver(replyTo: ActorRef[Coordinator.TsLoadingCommand])
  private type HandlerMessageType = DatasetLoader.Response | AddReceiver
  private def DatasetLoadingHandler(receivers: Set[ActorRef[Coordinator.TsLoadingCommand]]): Behavior[HandlerMessageType] =
    Behaviors.receiveMessage {
      case AddReceiver(replyTo) =>
        DatasetLoadingHandler(receivers + replyTo)
      case DatasetLoader.DatasetNTimeseries(n) =>
        receivers.foreach(_ ! Coordinator.DatasetHasNTimeseries(n))
        Behaviors.same
      case DatasetLoader.NewTimeSeries(_, tsIndex) =>
        receivers.foreach(_ ! Coordinator.NewTimeSeries(tsIndex))
        Behaviors.same
      case DatasetLoader.DatasetLoaded(_, indices) =>
        receivers.foreach(_ ! Coordinator.AllTimeSeriesLoaded(indices))
        Behaviors.stopped
      case DatasetLoader.DatasetNotLoaded(_, reason) =>
        receivers.foreach(_ ! Coordinator.FailedToLoadAllTimeSeries(reason))
        Behaviors.stopped
    }
}

private class TimeSeriesManager private (ctx: ActorContext[TsmProtocol.Command],
                                         stash: StashBuffer[TsmProtocol.Command]) {

  import TsmProtocol.*
  import TimeSeriesManager.*

  private val loader = ctx.spawn(DatasetLoader(ctx.self), "ts-loader", DatasetLoader.props)
  ctx.watch(loader)

  private def start(): Behavior[Command] = running(
    HashMap.empty[Int, HashMap[Int, LabeledTimeSeries]],
    HashMap.empty[Int, ActorRef[HandlerMessageType]]
  )

  private def running(
                       timeseries: HashMap[Int, Map[Int, LabeledTimeSeries]],
                       handlers: Map[Int, ActorRef[HandlerMessageType]]
                     ): Behavior[Command] = Behaviors.receiveMessage[Command] {
    case ReportStatus =>
      ctx.log.info(
        "[REPORT] Currently managing {} time series for {} datasets",
        timeseries.values.map(_.size).sum,
        timeseries.size
      )
      Behaviors.same

    case AddTimeSeries(datasetId, ts) =>
      running(
        timeseries.updatedWith(datasetId){
          case Some(mapping) => Some(mapping + (ts.id -> ts))
          case None => Some(Map(ts.id -> ts))
        },
        handlers
      )

    case m @ GetTimeSeries(datasetId, replyTo) =>
      timeseries.get(datasetId) match {
        case Some(ts) =>
          replyTo ! GetTimeSeriesResponse(ts.values.toArray.sortBy(_.idx))
        case None =>
          ctx.log.warn("Dataset d-{} not yet loaded, optimistically stashing {}", datasetId, m)
          stash.stash(m)
      }
      Behaviors.same

    case GetTimeSeriesIndices(Right(d), replyTo) =>
      timeseries.get(d.id) match {
        case Some(ts) =>
          replyTo ! Coordinator.DatasetHasNTimeseries(ts.size)
          ts.values.foreach(ts => replyTo ! Coordinator.NewTimeSeries(ts.idx))
          replyTo ! Coordinator.AllTimeSeriesLoaded(0 until ts.size)
          Behaviors.same
        case None =>
          handlers.get(d.id) match {
            case Some(handler) =>
              ctx.log.debug("Dataset d-{} is currently being loaded, waiting for response", d.id)
              handler ! AddReceiver(replyTo)
              Behaviors.same
            case None =>
              ctx.log.debug("Dataset d-{} not found, starting loading process", d.id)
              val loadingHandler = ctx.spawn(DatasetLoadingHandler(Set(replyTo)), f"loading-handler-${d.id}")
              ctx.watch(loadingHandler)
              loader ! DatasetLoader.LoadDataset(d, loadingHandler)
              running(timeseries, handlers + (d.id -> loadingHandler))
          }
      }

    case m@GetTimeSeriesIndices(Left(datasetId), replyTo) =>
      timeseries.get(datasetId) match {
        case Some(ts) =>
          replyTo ! Coordinator.DatasetHasNTimeseries(ts.size)
          ts.values.foreach(ts => replyTo ! Coordinator.NewTimeSeries(ts.idx))
          replyTo ! Coordinator.AllTimeSeriesLoaded(0 until ts.size)
        case None => handlers.get(datasetId) match {
          case Some(handler) =>
            ctx.log.debug("Dataset d-{} is currently being loaded, waiting for response", datasetId)
            handler ! AddReceiver(replyTo)
          case None =>
            ctx.log.warn("Dataset d-{} not yet loaded, optimistically stashing {}", datasetId, m)
            stash.stash(m)
        }
      }
      Behaviors.same

    case EvictDataset(datasetId) =>
      val newTimeseries = timeseries.filterNot{ case (id, _) => id == datasetId }
      ctx.log.debug("Evicted all time series for dataset d-{}", datasetId)
      running(newTimeseries, handlers)

    case m @ GetDatasetClassLabels(datasetId, replyTo) =>
      handlers.get(datasetId) match {
        case Some(_) =>
          ctx.log.debug("Dataset d-{} is currently being loaded, stashing {}", datasetId, m)
          stash.stash(m)
        case None =>
          timeseries.get(datasetId) match {
            case Some(ts) =>
              val labels = ts.values.toArray.sortBy(_.idx).map(_.label)
              replyTo ! DatasetClassLabels(labels)
            case None =>
              ctx.log.warn("Dataset d-{} not yet loaded, optimistically stashing {}", datasetId, m)
              stash.stash(m)
          }
      }
      Behaviors.same

    case m @ GetTSLengths(datasetId, replyTo) =>
      handlers.get(datasetId) match {
        case Some(_) =>
          ctx.log.debug("Dataset d-{} is currently being loaded, stashing {}", datasetId, m)
          stash.stash(m)
        case None =>
          timeseries.get(datasetId) match {
            case Some(ts) =>
              val lengths = ts.values.toArray.sortBy(_.idx).map(_.data.length)
              replyTo ! TSLengthsResponse(lengths)
            case None =>
              ctx.log.warn("Dataset d-{} not yet loaded, optimistically stashing {}", datasetId, m)
              stash.stash(m)
          }
      }
      Behaviors.same

  } receiveSignal {
    case (_, Terminated(localLoader)) =>
      stash.unstashAll(
        running(timeseries, handlers.filterNot(_._2.narrow == localLoader))
      )
  }
}
