package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat, NullOptions}

/**
 * All data models related to datasets and their marshalling.
 */
object DatasetModel {

  final case class Dataset(id: Int, name: String, testPath: String, trainPath: Option[String] = None)
  final case class Datasets(datasets: Seq[Dataset])

  given Ordering[Dataset] = Ordering.by(_.id)

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {
    given RootJsonFormat[Dataset] = jsonFormat4(Dataset.apply)

    given RootJsonFormat[Datasets] = jsonFormat1(Datasets.apply)
  }
}
