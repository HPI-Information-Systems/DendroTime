package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

/**
 * All data models related to datasets and their marshalling.
 */
object DatasetModel {

  final case class Dataset(id: Long, name: String, path: String)
  final case class Datasets(datasets: Seq[Dataset])

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    given RootJsonFormat[Dataset] = jsonFormat3(Dataset.apply)

    given RootJsonFormat[Datasets] = jsonFormat1(Datasets.apply)
  }
}
