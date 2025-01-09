package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object TimeSeriesModel {

  sealed trait TimeSeries {
    def id: Int
    def idx: Int
    def data: Array[Double]
  }
  final case class RawTimeSeries(id: Int, idx: Int, data: Array[Double]) extends TimeSeries
  final case class LabeledTimeSeries(id: Int, idx: Int, data: Array[Double], label: String) extends TimeSeries

  given Ordering[TimeSeries] = Ordering.by(_.id)

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[RawTimeSeries] = jsonFormat3(RawTimeSeries.apply)

    given RootJsonFormat[LabeledTimeSeries] = jsonFormat4(LabeledTimeSeries.apply)
  }
}
