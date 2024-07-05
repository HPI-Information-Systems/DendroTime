package de.hpi.fgis.dendrotime.api

import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import de.hpi.fgis.dendrotime.actors.DatasetRegistry
import de.hpi.fgis.dendrotime.model.DatasetModel
import de.hpi.fgis.dendrotime.Settings

class DatasetService(datasetRegistry: ActorRef[DatasetRegistry.Command])(using system: ActorSystem[_])
  extends Directives with DatasetModel.JsonSupport {

  import DatasetModel.*

  given timeout: Timeout = Settings(system).askTimeout

  lazy val route: Route = pathPrefix("datasets") {
    concat(
      pathEnd {
        concat(
          get {
            onSuccess(datasetRegistry.ask(DatasetRegistry.GetDatasets.apply)) { response =>
              complete(Datasets(response.datasets))
            }
          },
          post {
            entity(as[Dataset]) { dataset =>
              onSuccess(datasetRegistry.ask(DatasetRegistry.AddDataset(dataset, _))) {
                case DatasetRegistry.DatasetAdded(d) => complete(StatusCodes.Created, d)
                case DatasetRegistry.DatasetNotAdded(reason) => complete(StatusCodes.BadRequest, reason)
              }
            }
          }
        )
      },
      path(IntNumber) { id =>
        concat(
          get {
            onSuccess(datasetRegistry.ask(DatasetRegistry.GetDataset(id, _))) {
              case DatasetRegistry.GetDatasetResponse(Some(dataset)) => complete(dataset)
              case DatasetRegistry.GetDatasetResponse(None) => complete(StatusCodes.NotFound)
            }
          },
          delete {
            onSuccess(datasetRegistry.ask[DatasetRegistry.DatasetRemoved](DatasetRegistry.RemoveDataset(id, _))) { response =>
              complete(StatusCodes.OK, response.id.toString)
            }
          }
        )
      }
    )
  }
}
