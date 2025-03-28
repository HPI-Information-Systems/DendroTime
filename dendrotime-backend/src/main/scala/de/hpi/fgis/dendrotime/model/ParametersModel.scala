package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.distances.{Distance, DistanceOptions}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object ParametersModel {

  case class DendroTimeParams(distanceName: String,
                              linkageName: String,
                              strategy: String = "approx-distance-ascending") {
    if !areCompatible(distanceName, linkageName) then
      throw new IllegalArgumentException(s"$linkageName linkage is compatible with $distanceName")
    def distance(using DistanceOptions): Distance = Distance(distanceName)
    def linkage: Linkage = Linkage(linkageName)
  }

  private def areCompatible(distance: String, linkage: String): Boolean = {
    val euclideans = Seq("manhatten", "euclidean", "minkowsky")
    val linkagesRequireEuclideans = Seq("ward", "median", "centroid")
    !linkagesRequireEuclideans.contains(linkage) || euclideans.contains(distance)
  }

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[DendroTimeParams] = jsonFormat3(DendroTimeParams.apply)
  }
}
