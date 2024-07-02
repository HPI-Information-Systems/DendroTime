package de.hpi.fgis.dendrotime

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import de.hpi.fgis.dendrotime.actors.Server
import de.hpi.fgis.dendrotime.api.DatasetService

object Main {

  @main def DendroTimeServer(host: String = "localhost", port: Int = 8080): Unit = {
    val routes = api ~ assets
    val system = ActorSystem(Server(host, port, routes), "dendro-time-system")
    // CoordinatedShutdown(system).addActorTerminationTask(CoordinatedShutdown.PhaseServiceUnbind, "stop-http-server", system, Server.Stop)
    CoordinatedShutdown(system).addJvmShutdownHook({
      system ! Server.Stop
    })
  }

  private val api =
    pathPrefix("api") {
      Route.seal(concat(
        path("hello") {
          get {
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World!</h1>"))
          }
        },
        path("start") {
          post {
            complete(StatusCodes.NoContent)
          }
        },
        DatasetService.route,
      ))
    }

  private val assets =
    getFromResourceDirectory("frontend") ~
      pathPrefix("") {
        get {
          getFromResource("frontend/index.html", ContentTypes.`text/html(UTF-8)`)
        }
      }
}
