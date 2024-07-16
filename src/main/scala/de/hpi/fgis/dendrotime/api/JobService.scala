package de.hpi.fgis.dendrotime.api

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern.*
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import de.hpi.fgis.dendrotime.Settings

import scala.concurrent.duration.given
import de.hpi.fgis.dendrotime.actors.Scheduler
import de.hpi.fgis.dendrotime.model.DatasetModel
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object JobService {
  final case class Job(id: Long, dataset: DatasetModel.Dataset)

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol with DatasetModel.JsonSupport {
    given RootJsonFormat[Job] = jsonFormat2(Job.apply)
  }
}

class JobService(scheduler: ActorRef[Scheduler.Command])(using system: ActorSystem[_])
  extends Directives with JobService.JsonSupport {
  import JobService.*
  
  given timeout: Timeout = Settings(system).askTimeout

  lazy val route: Route = pathPrefix("jobs") {
    concat(
      pathEnd {
        concat(
          get {
            val datasets = scheduler.ask(Scheduler.GetStatus.apply)
            onSuccess(datasets) {
              case Scheduler.ProcessingStatus(id, Some(dataset)) => complete(Job(id, dataset))
              case Scheduler.ProcessingStatus(_, None) => complete(StatusCodes.NoContent)
            }
          },
          post {
            entity(as[DatasetModel.Dataset]) { dataset =>
              // store job
              onSuccess(scheduler.ask(Scheduler.StartProcessing(dataset, _))) {
                case Scheduler.ProcessingStarted(id) => complete(StatusCodes.Created, Job(id, dataset))
                case Scheduler.ProcessingRejected => complete(StatusCodes.Conflict, "Already processing a dataset")
              }
            }
          }
        )
      },
      path(LongNumber) { id =>
        concat(
          get {
            onSuccess(scheduler.ask[Scheduler.ProcessingStatus](Scheduler.GetStatus.apply)) { response =>
              given RootJsonFormat[Scheduler.ProcessingStatus] = jsonFormat2(Scheduler.ProcessingStatus.apply)
              complete(StatusCodes.OK, response)
            }
          },
          delete {
            onSuccess(scheduler.ask[Scheduler.ProcessingCancelled](Scheduler.CancelProcessing(id, _))) { response =>
              complete(StatusCodes.OK, response.cause)
            }
          }
        )
      },
    )
  }
}
