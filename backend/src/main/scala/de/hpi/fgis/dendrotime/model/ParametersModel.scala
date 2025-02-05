package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.distances.{Distance, DistanceOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object ParametersModel {

  case class DendroTimeParams(distanceName: String,
                              linkageName: String,
                              strategy: String = "approx-distance-ascending") {
    def distance(using DistanceOptions): Distance = Distance(distanceName)
    def linkage: Linkage = Linkage(linkageName)
  }

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[DendroTimeParams] = jsonFormat3(DendroTimeParams.apply)
  }
}