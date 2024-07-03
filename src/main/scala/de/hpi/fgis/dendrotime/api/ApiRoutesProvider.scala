package de.hpi.fgis.dendrotime.api

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import de.hpi.fgis.dendrotime.actors.{Scheduler, DatasetRegistry}

trait ApiRoutesProvider extends Directives {
  def createApiRoutes(routes: Route*): Route =
    val allApiRoutes = routes :+ path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World!</h1>"))
      }
    } :+ path("start") {
      post {
        complete(StatusCodes.NoContent)
      }
    }
    pathPrefix("api") {
      Route.seal(concat(allApiRoutes: _*))
    }
    
  def datasetServiceRoutes(datasetRegistry: ActorRef[DatasetRegistry.Command])(using ActorSystem[?]): Route =
    DatasetService(datasetRegistry).route
  
  def jobServiceRoutes(scheduler: ActorRef[Scheduler.Command])(using ActorSystem[?]): Route =
    JobService(scheduler).route
}
