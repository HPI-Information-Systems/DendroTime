package de.hpi.fgis.dendrotime.actors.tsmanager

import akka.actor.typed.ActorRef
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.io.TimeSeries.LabeledTimeSeries
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset

object TsmProtocol {
  sealed trait Command

  case class AddTimeSeries(datasetId: Int, timeseries: LabeledTimeSeries) extends Command

  case class EvictDataset(datasetId: Int) extends Command

  case class GetDatasetClassLabels(datasetId: Int, replyTo: ActorRef[DatasetClassLabelsResponse]) extends Command

  case class GetTimeSeriesIndices(dataset: Either[Int, Dataset], replyTo: ActorRef[Coordinator.TsLoadingCommand]) extends Command

  case class GetTSLengths(datasetId: Int, replyTo: ActorRef[TSLengthsResponse]) extends Command

  private[tsmanager] case object ReportStatus extends Command

  sealed trait DatasetClassLabelsResponse

  case class DatasetClassLabels(labels: Array[String]) extends DatasetClassLabelsResponse

  case object DatasetClassLabelsNotFound extends DatasetClassLabelsResponse

  case class TSLengthsResponse(lengths: IndexedSeq[Int])

  case class GetTimeSeries(dataset: Int, replyTo: ActorRef[GetTimeSeriesResponse]) extends Command

  case class GetTimeSeriesResponse(timeseries: IndexedSeq[LabeledTimeSeries])

}
