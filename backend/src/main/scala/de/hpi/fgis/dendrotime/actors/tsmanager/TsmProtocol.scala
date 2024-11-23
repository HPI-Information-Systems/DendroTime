package de.hpi.fgis.dendrotime.actors.tsmanager

import akka.actor.typed.ActorRef
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

import scala.collection.AbstractIterator
import scala.collection.immutable.HashMap
import scala.collection.immutable.Map.Map1
import scala.reflect.ClassTag

object TsmProtocol {
  sealed trait Command

  case class AddTimeSeries(datasetId: Int, timeseries: LabeledTimeSeries) extends Command

  case class EvictDataset(datasetId: Int) extends Command

  case class GetDatasetClassLabels(datasetId: Int, replyTo: ActorRef[DatasetClassLabelsResponse]) extends Command

  case class GetTimeSeriesIds(dataset: Either[Int, Dataset], replyTo: ActorRef[Coordinator.TsLoadingCommand]) extends Command

  case class GetTSLengths(datasetId: Int, replyTo: ActorRef[TSLengthsResponse]) extends Command

  case class GetTSIndexMapping(datasetId: Int, replyTo: ActorRef[TSIndexMappingResponse]) extends Command

  private[tsmanager] case object ReportStatus extends Command

  sealed trait DatasetClassLabelsResponse

  case class DatasetClassLabels(labels: Array[String]) extends DatasetClassLabelsResponse

  case object DatasetClassLabelsNotFound extends DatasetClassLabelsResponse

  case class TSLengthsResponse(lengths: Map[Long, Int])

  case class TSIndexMappingResponse(mapping: Map[Long, Int])

  sealed trait GetTimeSeries extends AbstractIterator[Long] with Command {
    val replyTo: ActorRef[GetTimeSeriesResponse]

    def replyWith(timeseries: HashMap[Long, LabeledTimeSeries]): Unit
  }

  object GetTimeSeries {
    // Use specialized implementations for small number of time series to optimize for speed and memory.
    def apply(tsId: Long, replyTo: ActorRef[GetTimeSeriesResponse]): GetTimeSeries =
      new GetTimeSeries1(tsId, replyTo)

    def apply(tsId1: Long, tsId2: Long, replyTo: ActorRef[GetTimeSeriesResponse]): GetTimeSeries =
      new GetTimeSeries2(tsId1, tsId2, replyTo)

    def apply(tsId1: Long, tsId2: Long, tsId3: Long, replyTo: ActorRef[GetTimeSeriesResponse]): GetTimeSeries =
      new GetTimeSeries3(tsId1, tsId2, tsId3, replyTo)

    def apply(tsId1: Long, tsId2: Long, tsId3: Long, tsId4: Long, replyTo: ActorRef[GetTimeSeriesResponse]): GetTimeSeries =
      new GetTimeSeries4(tsId1, tsId2, tsId3, tsId4, replyTo)

    def apply(tsIds: Array[Long], replyTo: ActorRef[GetTimeSeriesResponse]): GetTimeSeries =
      new GetTimeSeriesN(tsIds, replyTo)

    private final class GetTimeSeries1(
                                        val tsId: Long,
                                        override val replyTo: ActorRef[GetTimeSeriesResponse]
                                      ) extends GetTimeSeries {
      private var done = false
      override val size: Int = 1
      override val knownSize: Int = 1

      override def hasNext: Boolean = !done

      override def next(): Long = {
        done = true
        tsId
      }

      override def replyWith(timeseries: HashMap[Long, LabeledTimeSeries]): Unit = {
        timeseries.get(tsId) match {
          case Some(ts) => replyTo ! TimeSeriesFound(tsId, ts)
          case None => replyTo ! TimeSeriesNotFound
        }
      }
    }

    private final class GetTimeSeries2(
                                        val tsId1: Long,
                                        val tsId2: Long,
                                        override val replyTo: ActorRef[GetTimeSeriesResponse]
                                      ) extends GetTimeSeries {
      private var i = 0
      override val size: Int = 2
      override val knownSize: Int = 2

      override def hasNext: Boolean = i < 2

      override def next(): Long = {
        val res = i match {
          case 0 => tsId1
          case 1 => tsId2
        }
        i += 1
        res
      }

      override def replyWith(timeseries: HashMap[Long, LabeledTimeSeries]): Unit = {
        val ts1 = timeseries.get(tsId1)
        val ts2 = timeseries.get(tsId2)
        if ts1.isDefined && ts2.isDefined then
          replyTo ! TimeSeriesFound(tsId1, ts1.get, tsId2, ts2.get)
        else
          replyTo ! TimeSeriesNotFound
      }
    }

    private final class GetTimeSeries3(
                                        val tsId1: Long,
                                        val tsId2: Long,
                                        val tsId3: Long,
                                        override val replyTo: ActorRef[GetTimeSeriesResponse]
                                      ) extends GetTimeSeries {
      private var i = 0
      override val size: Int = 3
      override val knownSize: Int = 3

      override def hasNext: Boolean = i < 3

      override def next(): Long = {
        val res = i match {
          case 0 => tsId1
          case 1 => tsId2
          case 2 => tsId3
        }
        i += 1
        res
      }

      override def replyWith(timeseries: HashMap[Long, LabeledTimeSeries]): Unit = {
        val ts1 = timeseries.get(tsId1)
        val ts2 = timeseries.get(tsId2)
        val ts3 = timeseries.get(tsId3)
        if ts1.isDefined && ts2.isDefined && ts3.isDefined then
          replyTo ! TimeSeriesFound(tsId1, ts1.get, tsId2, ts2.get, tsId3, ts3.get)
        else
          replyTo ! TimeSeriesNotFound
      }
    }

    private final class GetTimeSeries4(
                                        val tsId1: Long,
                                        val tsId2: Long,
                                        val tsId3: Long,
                                        val tsId4: Long,
                                        override val replyTo: ActorRef[GetTimeSeriesResponse]
                                      ) extends GetTimeSeries {
      private var i = 0
      override val size: Int = 4
      override val knownSize: Int = 4

      override def hasNext: Boolean = i < 4

      override def next(): Long = {
        val res = i match {
          case 0 => tsId1
          case 1 => tsId2
          case 2 => tsId3
          case 3 => tsId4
        }
        i += 1
        res
      }

      override def replyWith(timeseries: HashMap[Long, LabeledTimeSeries]): Unit = {
        val ts1 = timeseries.get(tsId1)
        val ts2 = timeseries.get(tsId2)
        val ts3 = timeseries.get(tsId3)
        val ts4 = timeseries.get(tsId4)
        if ts1.isDefined && ts2.isDefined && ts3.isDefined && ts4.isDefined then
          replyTo ! TimeSeriesFound(tsId1, ts1.get, tsId2, ts2.get, tsId3, ts3.get, tsId4, ts4.get)
        else
          replyTo ! TimeSeriesNotFound
      }
    }

    private final class GetTimeSeriesN(
                                        val tsIds: Array[Long],
                                        override val replyTo: ActorRef[GetTimeSeriesResponse]
                                      ) extends GetTimeSeries {
      private var i = 0
      override val size: Int = tsIds.length
      override val knownSize: Int = tsIds.length

      override def hasNext: Boolean = i < tsIds.length

      override def next(): Long = {
        val res = tsIds(i)
        i += 1
        res
      }

      override def replyWith(timeseries: HashMap[Long, LabeledTimeSeries]): Unit = {
        val found = tsIds.flatMap(timeseries.get)
        if found.length == size then
          replyTo ! TimeSeriesFound(tsIds, found)
        else
          replyTo ! TimeSeriesNotFound
      }
    }
  }

  sealed trait GetTimeSeriesResponse

  case object TimeSeriesNotFound extends GetTimeSeriesResponse

  trait TimeSeriesFound extends GetTimeSeriesResponse with Iterable[(Long, LabeledTimeSeries)] {
    def apply(key: Long): LabeledTimeSeries

    def contains(key: Long): Boolean

    def get(key: Long): Option[LabeledTimeSeries] = if contains(key) then Some(apply(key)) else None
  }

  object TimeSeriesFound {
    // Use specialized implementations for small number of time series to optimize for speed and memory.
    def apply(id: Long, ts: LabeledTimeSeries): GetTimeSeriesResponse =
      new TimeSeriesFound1(id, ts)

    def apply(id1: Long, ts1: LabeledTimeSeries, id2: Long, ts2: LabeledTimeSeries): GetTimeSeriesResponse =
      new TimeSeriesFound2(id1, ts1, id2, ts2)

    def apply(id1: Long, ts1: LabeledTimeSeries, id2: Long, ts2: LabeledTimeSeries,
              id3: Long, ts3: LabeledTimeSeries): GetTimeSeriesResponse =
      new TimeSeriesFound3(id1, ts1, id2, ts2, id3, ts3)

    def apply(id1: Long, ts1: LabeledTimeSeries, id2: Long, ts2: LabeledTimeSeries,
              id3: Long, ts3: LabeledTimeSeries, id4: Long, ts4: LabeledTimeSeries): GetTimeSeriesResponse =
      new TimeSeriesFound4(id1, ts1, id2, ts2, id3, ts3, id4, ts4)

    def apply(ids: Array[Long], timeseries: Array[LabeledTimeSeries]): GetTimeSeriesResponse =
      val b = HashMap.newBuilder[Long, LabeledTimeSeries]
      var i = 0
      while i < ids.length do
        b += ids(i) -> timeseries(i)
        i += 1
      new TimeSeriesFoundN(b.result())
  }

  private final class TimeSeriesFound1(id: Long, ts: LabeledTimeSeries) extends TimeSeriesFound {
    override def contains(key: Long): Boolean = key == id

    override def apply(key: Long): LabeledTimeSeries =
      if key == id then ts
      else throw new NoSuchElementException(s"key not found: $key")

    override def iterator: Iterator[(Long, LabeledTimeSeries)] = Iterator.single((id, ts))
  }

  private final class TimeSeriesFound2(id1: Long, ts1: LabeledTimeSeries,
                                       id2: Long, ts2: LabeledTimeSeries) extends TimeSeriesFound {
    override def contains(key: Long): Boolean = key == id1 || key == id2

    override def apply(key: Long): LabeledTimeSeries =
      if key == id1 then ts1
      else if key == id2 then ts2
      else throw new NoSuchElementException(s"key not found: $key")

    override def iterator: Iterator[(Long, LabeledTimeSeries)] = Iterator((id1, ts1), (id2, ts2))
  }

  private final class TimeSeriesFound3(id1: Long, ts1: LabeledTimeSeries,
                                       id2: Long, ts2: LabeledTimeSeries,
                                       id3: Long, ts3: LabeledTimeSeries) extends TimeSeriesFound {
    override def contains(key: Long): Boolean = key == id1 || key == id2 || key == id3

    override def apply(key: Long): LabeledTimeSeries =
      if key == id1 then ts1
      else if key == id2 then ts2
      else if key == id3 then ts3
      else throw new NoSuchElementException(s"key not found: $key")

    override def iterator: Iterator[(Long, LabeledTimeSeries)] = Iterator((id1, ts1), (id2, ts2), (id3, ts3))
  }

  private final class TimeSeriesFound4(id1: Long, ts1: LabeledTimeSeries,
                                       id2: Long, ts2: LabeledTimeSeries,
                                       id3: Long, ts3: LabeledTimeSeries,
                                       id4: Long, ts4: LabeledTimeSeries) extends TimeSeriesFound {
    override def contains(key: Long): Boolean = key == id1 || key == id2 || key == id3 || key == id4

    override def apply(key: Long): LabeledTimeSeries =
      if key == id1 then ts1
      else if key == id2 then ts2
      else if key == id3 then ts3
      else if key == id4 then ts4
      else throw new NoSuchElementException(s"key not found: $key")

    override def iterator: Iterator[(Long, LabeledTimeSeries)] =
      Iterator((id1, ts1), (id2, ts2), (id3, ts3), (id4, ts4))
  }

  private final class TimeSeriesFoundN(tsMap: HashMap[Long, LabeledTimeSeries]) extends TimeSeriesFound {
    override def contains(key: Long): Boolean = tsMap.contains(key)

    override def apply(key: Long): LabeledTimeSeries = tsMap(key)

    override def iterator: Iterator[(Long, LabeledTimeSeries)] = tsMap.iterator
  }
}
