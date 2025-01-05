package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.distances.{Distance, DistanceOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat}

object ParametersModel {

  case class DendroTimeParams(distanceName: String,
                              linkageName: String,
                              strategy: String = "approx-distance-ascending") {
    def distance(using DistanceOptions): Distance = Distance(distanceName)
    def linkage: Linkage = Linkage(linkageName)
  }

  val DefaultParams: DendroTimeParams = DendroTimeParams("msm", "ward")

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[Linkage] = new RootJsonFormat[Linkage] {
      override def read(json: JsValue): Linkage = json match {
        case JsString(value) => Linkage(value)
        case _ => throw DeserializationException("Linkage must be a string")
      }
      override def write(obj: Linkage): JsValue = JsString(Linkage.unapply(obj))
    }

    given RootJsonFormat[DendroTimeParams] = jsonFormat3(DendroTimeParams.apply)
  }
}