package de.hpi.fgis.dendrotime

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.server.Directives.*
import com.typesafe.config.{Config, ConfigFactory}
import de.hpi.fgis.dendrotime.actors.Server

object Main {

  @main def DendroTimeServer(): Unit = {
    val system = ActorSystem(Server(assets), "dendro-time-system", configuration)
    // CoordinatedShutdown(system).addActorTerminationTask(CoordinatedShutdown.PhaseServiceUnbind, "stop-http-server", system, Server.Stop)
    CoordinatedShutdown(system).addJvmShutdownHook({
      system ! Server.Stop
    })
  }

  lazy val configuration: Config = ConfigFactory.load()

  private val assets =
    getFromResourceDirectory("frontend") ~
      pathPrefix("") {
        get {
          getFromResource("frontend/index.html", ContentTypes.`text/html(UTF-8)`)
        }
      }
}
