package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.{ActorRef, Behavior, Terminated}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

import scala.collection.immutable.{HashMap, Set}
import scala.concurrent.duration.*


object TimeSeriesManager {

  sealed trait Command
  case class AddTimeSeries(datasetId: Int, timeseries: LabeledTimeSeries) extends Command
  case class GetTimeSeries(timeseriesId: Long, replyTo: ActorRef[GetTimeSeriesResponse]) extends Command
  case class GetTimeSeriesIds(dataset: Either[Int, Dataset], replyTo: ActorRef[GetTimeSeriesIdsResponse]) extends Command
  case class EvictDataset(datasetId: Int) extends Command
  private case object StatusTick extends Command

  sealed trait GetTimeSeriesResponse
  case class TimeSeriesFound(timeseries: LabeledTimeSeries) extends GetTimeSeriesResponse
  case class TimeSeriesNotFound(id: Long) extends GetTimeSeriesResponse

  sealed trait GetTimeSeriesIdsResponse
  case class TimeSeriesIdsFound(datasetId: Int, ids: Set[Long]) extends GetTimeSeriesIdsResponse
  case class FailedToLoadTimeSeriesIds(datasetId: Int, cause: String) extends GetTimeSeriesIdsResponse

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(StatusTick, 30.seconds)
      new TimeSeriesManager(ctx).start()
    }
  }

  private final case class AddReceiver(replyTo: ActorRef[GetTimeSeriesIdsResponse])
  private def DatasetLoadingHandler(receivers: Set[ActorRef[GetTimeSeriesIdsResponse]]): Behavior[DatasetLoader.Response | AddReceiver] =
    Behaviors.setup(ctx => Behaviors.receiveMessage {
      case AddReceiver(replyTo) =>
        DatasetLoadingHandler(receivers + replyTo)
      case DatasetLoader.DatasetLoaded(id, tsIds) =>
        ctx.log.debug("Received dataset loaded message for dataset d-{}, forwarding", id)
        receivers.foreach(_ ! TimeSeriesIdsFound(id, tsIds.toSet))
        Behaviors.stopped
      case DatasetLoader.DatasetNotLoaded(id, reason) =>
        ctx.log.error("Failed to load dataset d-{}, forwarding message. Reason: {}", id, reason)
        receivers.foreach(_ ! FailedToLoadTimeSeriesIds(id, reason))
        Behaviors.stopped
      case DatasetLoader.NewTimeSeries(datasetId, tsId) =>
        // FIXME: forward notifications to coordinator and start distance calculation early
        ctx.log.debug("Received new time series message: currently unhandled")
        Behaviors.same
    })
}

private class TimeSeriesManager private (ctx: ActorContext[TimeSeriesManager.Command]) {

  import TimeSeriesManager.*

  private val loader = ctx.spawn(DatasetLoader(ctx.self), "ts-loader")
  ctx.watch(loader)

  private def start(): Behavior[TimeSeriesManager.Command] = running(
    HashMap.empty[Long, LabeledTimeSeries],
    HashMap.empty[Int, Set[Long]],
    HashMap.empty[Int, ActorRef[DatasetLoader.Response | AddReceiver]]
  )

  private def running(
                       timeseries: HashMap[Long, LabeledTimeSeries],
                       datasetMapping: HashMap[Int, Set[Long]],
                       handlers: Map[Int, ActorRef[DatasetLoader.Response | AddReceiver]]
                     ): Behavior[TimeSeriesManager.Command] = Behaviors.receiveMessage[TimeSeriesManager.Command] {
    case StatusTick =>
      ctx.log.info("STATUS: Currently managing {} time series for {} datasets", timeseries.size, datasetMapping.size)
      Behaviors.same
    case AddTimeSeries(datasetId, ts) =>
      running(
        timeseries + (ts.id -> ts),
        datasetMapping.updatedWith(datasetId){
          case Some(mapping) => Some(mapping + ts.id)
          case None => Some(Set.empty)
        },
        handlers
      )
    case GetTimeSeries(id, replyTo) =>
      timeseries.get(id) match {
        case Some(ts) => replyTo ! TimeSeriesFound(ts)
        case None => replyTo ! TimeSeriesNotFound(id)
      }
      Behaviors.same
    case GetTimeSeriesIds(Right(d), replyTo) =>
      datasetMapping.get(d.id) match {
        case Some(ids) =>
          replyTo ! TimeSeriesIdsFound(d.id, ids)
          Behaviors.same
        case None =>
          handlers.get(d.id) match {
            case Some(handler) =>
              ctx.log.debug("Dataset d-{} is currently being loaded, waiting for response", d.id)
              handler ! AddReceiver(replyTo)
              Behaviors.same
            case None =>
              ctx.log.info("Dataset d-{} not found, starting loading process", d.id)
              val loadingHandler = ctx.spawn(DatasetLoadingHandler(Set(replyTo)), f"loading-handler-${d.id}")
              ctx.watch(loadingHandler)
              loader ! DatasetLoader.LoadDataset(d.id, d.path, loadingHandler)
              running(timeseries, datasetMapping, handlers + (d.id -> loadingHandler))
          }
      }
    case GetTimeSeriesIds(Left(datasetId), replyTo) =>
      datasetMapping.get(datasetId) match {
        case Some(ids) => replyTo ! TimeSeriesIdsFound(datasetId, ids)
        case None => handlers.get(datasetId) match {
          case Some(handler) =>
            ctx.log.debug("Dataset d-{} is currently being loaded, waiting for response", datasetId)
            handler ! AddReceiver(replyTo)
          case None =>
            replyTo ! FailedToLoadTimeSeriesIds(datasetId, "Not found")
        }
      }
      Behaviors.same
    case EvictDataset(datasetId) =>
      val tsIds = datasetMapping.getOrElse(datasetId, Set.empty)
      val newTimeseries = timeseries.filterNot{ case (id, _) => tsIds.contains(id) }
      ctx.log.info("Evicted {} time series of dataset d-{}", timeseries.size - newTimeseries.size, datasetId)
      running(newTimeseries, datasetMapping - datasetId, handlers)
  }.receiveSignal{
    case (_, Terminated(loader)) =>
      running(timeseries, datasetMapping, handlers.filterNot(_._2.narrow == loader))
  }
}
