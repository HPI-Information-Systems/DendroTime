package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.{LabeledTimeSeries, TimeSeries}

import scala.collection.immutable.{HashMap, Set}


object TimeSeriesManager {

  sealed trait Command
  case class AddTimeSeries(datasetId: Int, timeseries: LabeledTimeSeries) extends Command
  case class GetTimeSeries(tsId1: Long, tsId2: Long, replyTo: ActorRef[GetTimeSeriesResponse]) extends Command
  case class GetTimeSeriesIds(dataset: Either[Int, Dataset], replyTo: ActorRef[Coordinator.TsLoadingCommand]) extends Command
  case class EvictDataset(datasetId: Int) extends Command
  case class GetDatasetClassLabels(datasetId: Int, replyTo: ActorRef[DatasetClassLabelsResponse]) extends Command
  case class GetTSLengths(datasetId: Int, replyTo: ActorRef[TSLengthsResponse]) extends Command
  case class GetTSIndexMapping(datasetId: Int, replyTo: ActorRef[TSIndexMappingResponse]) extends Command
  private case object ReportStatus extends Command

  sealed trait GetTimeSeriesResponse
  case class TimeSeriesFound(ts1: TimeSeries, ts2: TimeSeries) extends GetTimeSeriesResponse
  case class TimeSeriesNotFound(id: Long) extends GetTimeSeriesResponse

  sealed trait DatasetClassLabelsResponse
  case class DatasetClassLabels(labels: Array[String]) extends DatasetClassLabelsResponse
  case object DatasetClassLabelsNotFound extends DatasetClassLabelsResponse

  case class TSLengthsResponse(lengths: Map[Long, Int])

  case class TSIndexMappingResponse(mapping: Map[Long, Int])

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(ReportStatus, Settings(ctx.system).reportingInterval)
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
      case DatasetLoader.NewTimeSeries(_, tsId) =>
        receivers.foreach(_ ! Coordinator.NewTimeSeries(tsId))
        Behaviors.same
      case DatasetLoader.DatasetLoaded(_, tsIds) =>
        receivers.foreach(_ ! Coordinator.AllTimeSeriesLoaded(tsIds.toSet))
        Behaviors.stopped
      case DatasetLoader.DatasetNotLoaded(_, reason) =>
        receivers.foreach(_ ! Coordinator.FailedToLoadAllTimeSeries(reason))
        Behaviors.stopped
    }
}

private class TimeSeriesManager private (ctx: ActorContext[TimeSeriesManager.Command],
                                         stash: StashBuffer[TimeSeriesManager.Command]) {

  import TimeSeriesManager.*

  private val loader = ctx.spawn(DatasetLoader(ctx.self), "ts-loader", DatasetLoader.props)
  ctx.watch(loader)

  private def start(): Behavior[TimeSeriesManager.Command] = running(
    HashMap.empty[Long, LabeledTimeSeries],
    HashMap.empty[Int, Set[Long]],
    HashMap.empty[Int, ActorRef[HandlerMessageType]]
  )

  private def running(
                       timeseries: HashMap[Long, LabeledTimeSeries],
                       datasetMapping: HashMap[Int, Set[Long]],
                       handlers: Map[Int, ActorRef[HandlerMessageType]]
                     ): Behavior[TimeSeriesManager.Command] = Behaviors.receiveMessage[TimeSeriesManager.Command] {
    case ReportStatus =>
      ctx.log.info("[REPORT] Currently managing {} time series for {} datasets", timeseries.size, datasetMapping.size)
      Behaviors.same

    case AddTimeSeries(datasetId, ts) =>
      running(
        timeseries + (ts.id -> ts),
        datasetMapping.updatedWith(datasetId){
          case Some(mapping) => Some(mapping + ts.id)
          case None => Some(Set(ts.id))
        },
        handlers
      )

    case GetTimeSeries(id1, id2, replyTo) =>
      val ts1 = timeseries.get(id1)
      val ts2 = timeseries.get(id2)
      if ts1.nonEmpty && ts2.nonEmpty then
        replyTo ! TimeSeriesFound(ts1.get, ts2.get)
      else if ts1.isEmpty then
        replyTo ! TimeSeriesNotFound(id1)
      else if ts2.isEmpty then
        replyTo ! TimeSeriesNotFound(id2)
      Behaviors.same

    case GetTimeSeriesIds(Right(d), replyTo) =>
      datasetMapping.get(d.id) match {
        case Some(ids) =>
          replyTo ! Coordinator.DatasetHasNTimeseries(ids.size)
          ids.foreach(replyTo ! Coordinator.NewTimeSeries(_))
          replyTo ! Coordinator.AllTimeSeriesLoaded(ids)
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
              running(timeseries, datasetMapping, handlers + (d.id -> loadingHandler))
          }
      }

    case m@GetTimeSeriesIds(Left(datasetId), replyTo) =>
      datasetMapping.get(datasetId) match {
        case Some(ids) =>
          replyTo ! Coordinator.DatasetHasNTimeseries(ids.size)
          ids.foreach(replyTo ! Coordinator.NewTimeSeries(_))
          replyTo ! Coordinator.AllTimeSeriesLoaded(ids)
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
      val tsIds = datasetMapping.getOrElse(datasetId, Set.empty)
      val newTimeseries = timeseries.filterNot{ case (id, _) => tsIds.contains(id) }
      ctx.log.debug("Evicted {} time series of dataset d-{}", timeseries.size - newTimeseries.size, datasetId)
      running(newTimeseries, datasetMapping - datasetId, handlers)

    case m @ GetDatasetClassLabels(datasetId, replyTo) =>
      handlers.get(datasetId) match {
        case Some(_) =>
          ctx.log.debug("Dataset d-{} is currently being loaded, stashing {}", datasetId, m)
          stash.stash(m)
        case None =>
          datasetMapping.get(datasetId) match {
            case Some(ids) =>
              val labels = ids.toArray.sorted.map(timeseries(_).label)
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
          datasetMapping.get(datasetId) match {
            case Some(ids) =>
              val lengths = ids.map(id => (id, timeseries(id).data.length)).toMap
              replyTo ! TSLengthsResponse(lengths)
            case None =>
              ctx.log.warn("Dataset d-{} not yet loaded, optimistically stashing {}", datasetId, m)
              stash.stash(m)
          }
      }
      Behaviors.same

    case m @ GetTSIndexMapping(datasetId, replyTo) =>
      handlers.get(datasetId) match {
        case Some(_) =>
          ctx.log.debug("Dataset d-{} is currently being loaded, stashing {}", datasetId, m)
          stash.stash(m)
        case None =>
          datasetMapping.get(datasetId) match {
            case Some(ids) =>
              val mapping = ids.map(id => (id, timeseries(id).idx)).toMap
              replyTo ! TSIndexMappingResponse(mapping)
            case None =>
              ctx.log.warn("Dataset d-{} not yet loaded, optimistically stashing {}", datasetId, m)
              stash.stash(m)
          }
      }
      Behaviors.same

  } receiveSignal {
    case (_, Terminated(localLoader)) =>
      stash.unstashAll(
        running(timeseries, datasetMapping, handlers.filterNot(_._2.narrow == localLoader))
      )
  }
}
