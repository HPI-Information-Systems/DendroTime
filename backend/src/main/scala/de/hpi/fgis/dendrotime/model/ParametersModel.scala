package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.distances.Distance
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat}

object ParametersModel {
  
  case class DendroTimeParams(metric: Distance, linkage: Linkage, approxLength: Int = 10)

  val DefaultParams: DendroTimeParams = DendroTimeParams(Distance("msm"), Linkage("average"))
  
  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[Distance] = new RootJsonFormat[Distance] {
      override def read(json: JsValue): Distance = json match {
        case JsString(value) => Distance(value)
        case _ => throw DeserializationException("Distance must be a string")
      }
      override def write(obj: Distance): JsValue = JsString(Distance.unapply(obj))
    }
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