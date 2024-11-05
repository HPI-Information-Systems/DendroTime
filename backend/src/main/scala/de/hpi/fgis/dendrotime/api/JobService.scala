package de.hpi.fgis.dendrotime.api

import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.Scheduler
import de.hpi.fgis.dendrotime.model.StateModel.ProgressMessage
import de.hpi.fgis.dendrotime.model.{DatasetModel, ParametersModel, StateModel}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.util.{Failure, Success, Try}

object JobService {
  final case class Job(id: Long, dataset: DatasetModel.Dataset)
  final case class ProcessingRequestBody(dataset: DatasetModel.Dataset, params: ParametersModel.DendroTimeParams)

  trait JsonSupport
    extends SprayJsonSupport
      with DefaultJsonProtocol with DatasetModel.JsonSupport
      with StateModel.JsonSupport with ParametersModel.JsonSupport {
    given RootJsonFormat[Job] = jsonFormat2(Job.apply)
    given RootJsonFormat[ProcessingRequestBody] = jsonFormat2(ProcessingRequestBody.apply)
    given RootJsonFormat[Scheduler.ProcessingStatus] = jsonFormat2(Scheduler.ProcessingStatus.apply)
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
            onSuccess(scheduler.ask(Scheduler.GetStatus.apply)) {
              case Scheduler.ProcessingStatus(id, Some(dataset)) => complete(Job(id, dataset))
              case Scheduler.ProcessingStatus(_, None) => complete(StatusCodes.NoContent)
            }
          },
          post {
            entity(as[ProcessingRequestBody]) { body =>
              onSuccess(scheduler.ask(Scheduler.StartProcessing(body.dataset, body.params, _))) {
                case Scheduler.ProcessingStarted(id) => complete(StatusCodes.Created, Job(id, body.dataset))
                case Scheduler.ProcessingRejected => complete(StatusCodes.Conflict, "Already processing a dataset")
              }
            }
          }
        )
      },
      pathPrefix(LongNumber) { id =>
        concat(
          pathEnd {
            concat(
              get {
                onSuccess(scheduler.ask(Scheduler.GetStatus.apply)) { response =>
                  complete(StatusCodes.OK, response)
                }
              },
              post {
                entity(as[String]) {
                  case "pause" => complete(StatusCodes.NotImplemented)
                  case "resume" => complete(StatusCodes.NotImplemented)
                  case "stop" => onSuccess(scheduler.ask[Try[Unit]](Scheduler.StopProcessing(id, _))) {
                    case Failure(response) => complete(StatusCodes.BadRequest, response.getMessage)
                    case Success(_) => complete(StatusCodes.OK)
                  }
                  case _ => complete(StatusCodes.BadRequest, "Invalid command")
                }
              },
              delete {
                onSuccess(scheduler.ask[Scheduler.ProcessingCancelled](Scheduler.CancelProcessing(id, _))) { response =>
                  complete(StatusCodes.OK, response.cause)
                }
              }
            )
          },
          path("progress") {
            get {
              onSuccess(scheduler.ask(Scheduler.GetProgress(id, _))) {
                case ProgressMessage.Unchanged => complete(StatusCodes.NoContent)
                case response: ProgressMessage => complete(StatusCodes.OK, response)
              }
            }
          }
        )
      }
    )
  }
}
