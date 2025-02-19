package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.io.TimeSeries.{LabeledTimeSeries, RawTimeSeries}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object TimeSeriesModel {
  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[RawTimeSeries] = jsonFormat3(RawTimeSeries.apply)

    given RootJsonFormat[LabeledTimeSeries] = jsonFormat4(LabeledTimeSeries.apply)
  }
}
