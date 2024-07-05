package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

import scala.collection.immutable.{HashMap, Set}


object TimeSeriesManager {

  sealed trait Command
  case class AddTimeSeries(datasetId: Int, timeseries: LabeledTimeSeries) extends Command
  case class GetTimeSeries(timeseriesId: Long, replyTo: ActorRef[GetTimeSeriesResponse]) extends Command
  case class EvictDataset(datasetId: Int) extends Command
  
  sealed trait GetTimeSeriesResponse
  case class TimeSeriesFound(timeseries: LabeledTimeSeries) extends GetTimeSeriesResponse
  case object TimeSeriesNotFound extends GetTimeSeriesResponse
  
  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    new TimeSeriesManager(ctx).start()
  }
}

private class TimeSeriesManager(ctx: ActorContext[TimeSeriesManager.Command]) {
  
    import TimeSeriesManager.*
    
    def start(): Behavior[TimeSeriesManager.Command] = running(
      HashMap.empty[Long, LabeledTimeSeries], 
      HashMap.empty[Int, Set[Long]]
    )
      
    private def running(
                         timeseries: HashMap[Long, LabeledTimeSeries],
                         datasetMapping: HashMap[Int, Set[Long]]
                       ): Behavior[TimeSeriesManager.Command] = Behaviors.receiveMessage {
      case AddTimeSeries(datasetId, ts) =>
        running(
          timeseries + (ts.id -> ts),
          datasetMapping.updatedWith(datasetId){
            case Some(mapping) => Some(mapping + ts.id)
            case None => Some(Set.empty)
          }
        )
      case GetTimeSeries(id, replyTo) =>
        timeseries.get(id) match {
          case Some(ts) => replyTo ! TimeSeriesFound(ts)
          case None => replyTo ! TimeSeriesNotFound
        }
        Behaviors.same
      case EvictDataset(datasetId) =>
        val tsIds = datasetMapping.getOrElse(datasetId, Set.empty)
        val newTimeseries = timeseries.filterNot{ case (id, _) => tsIds.contains(id) }
        ctx.log.info("Evicted {} time series of dataset {}", timeseries.size - newTimeseries.size, datasetId)
        running(newTimeseries, datasetMapping - datasetId)
    }
}
