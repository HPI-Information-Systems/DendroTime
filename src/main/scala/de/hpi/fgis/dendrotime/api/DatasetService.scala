package de.hpi.fgis.dendrotime.api

import akka.http.scaladsl.server.{Directives, Route}
import de.hpi.fgis.dendrotime.model.DatasetModel

object DatasetService extends Directives with DatasetModel.JsonSupport {

  import DatasetModel.*

  lazy val route: Route = pathPrefix("datasets") {
    concat(
      pathEnd {
        concat(
          get {
            // load list of all datasets
            complete(Datasets(Seq.empty))
          },
          post {
            entity(as[Dataset]) { dataset =>
              // store dataset
              complete(dataset)
            }
          }
          // delete?
        )
      },
      (get & path(LongNumber)) { id =>
        complete(Dataset(id, "Dataset " + id, "/path/to/dataset/" + id))
      },
    )
  }
}
