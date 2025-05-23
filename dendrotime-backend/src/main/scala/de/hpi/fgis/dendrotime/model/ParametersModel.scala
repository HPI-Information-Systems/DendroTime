package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.distances.Distance
import de.hpi.fgis.dendrotime.clustering.distances.DistanceOptions.AllDistanceOptions
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object ParametersModel {

  object DendroTimeParams {
    def apply(distanceName: String,
              linkageName: String,
              strategy: String = "approx-distance-ascending"): DendroTimeParams =
      new DendroTimeParams(distanceName.toLowerCase, linkageName.toLowerCase, strategy.toLowerCase)
  }

  case class DendroTimeParams(distanceName: String,
                              linkageName: String,
                              strategy: String = "approx-distance-ascending") {
    if !areCompatible(distanceName, linkageName) then
      println(
        s"WARNING: $linkageName linkage is NOT compatible with $distanceName, " +
        "proceed at your own risk!"
      )
      // throw new IllegalArgumentException(s"$linkageName linkage is NOT compatible with $distanceName")

    def distance(using AllDistanceOptions): Distance = Distance(distanceName)

    def linkage: Linkage = Linkage(linkageName)
  }

  private def areCompatible(distance: String, linkage: String): Boolean = {
    val euclideans = Seq("manhatten", "euclidean", "minkowsky")
    val linkagesRequireEuclideans = Seq("ward", "median", "centroid")
    // val linkagesRequireEuclideans = Seq.empty
    !linkagesRequireEuclideans.contains(linkage) || euclideans.contains(distance)
  }

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[DendroTimeParams] = jsonFormat3(DendroTimeParams.apply)
  }
}
