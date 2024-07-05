package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object TimeSeriesModel {

  sealed trait TimeSeries {
    def id: Long
    def data: Array[Double]
  }
  final case class RawTimeSeries(id: Long, data: Array[Double]) extends TimeSeries
  final case class LabeledTimeSeries(id: Long, data: Array[Double], label: String) extends TimeSeries

  given Ordering[TimeSeries] = Ordering.by(_.id)

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[RawTimeSeries] = jsonFormat2(RawTimeSeries.apply)

    given RootJsonFormat[LabeledTimeSeries] = jsonFormat3(LabeledTimeSeries.apply)
  }
}
