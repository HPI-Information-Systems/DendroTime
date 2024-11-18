package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.distances.{Distance, DistanceOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat}

object ParametersModel {

  case class DendroTimeParams(metricName: String,
                              linkageName: String,
                              strategy: String = "fcfs",
                              approxLength: Int = 10) {
    def metric(using DistanceOptions): Distance = Distance(metricName)
    def linkage: Linkage = Linkage(linkageName)
  }

  val DefaultParams: DendroTimeParams = DendroTimeParams("msm", "average")

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[Linkage] = new RootJsonFormat[Linkage] {
      override def read(json: JsValue): Linkage = json match {
        case JsString(value) => Linkage(value)
        case _ => throw DeserializationException("Linkage must be a string")
      }
      override def write(obj: Linkage): JsValue = JsString(Linkage.unapply(obj))
    }

    given RootJsonFormat[DendroTimeParams] = jsonFormat4(DendroTimeParams.apply)
  }
}