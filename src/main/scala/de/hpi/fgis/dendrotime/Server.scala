package de.hpi.fgis.dendrotime

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn


@main def DendroTimeServer(host: String = "localhost", port: Int = 8080): Unit = {
  given system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "dendro-time-system")
  import system.given
  
  Server.start(host, port)
}

object Server {
  def start(host: String, port: Int)(using system: ActorSystem[Any], ctx: ExecutionContextExecutor): Unit =
    val route =
      pathPrefix("api") {
        Route.seal(concat(
          path("hello") {
            get {
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World!</h1>"))
            }
          })
        )
      } ~ assets

    val bindingFuture = Http().newServerAt(host, port).bind(route)

    println(s"Server is now online. Open http://$host:$port/ to view.\n Press RETURN to stop ...")
//    StdIn.readLine()
//    bindingFuture
//      .flatMap(_.unbind())
//      .onComplete(_ => system.terminate())

  private val assets =
    getFromResourceDirectory("frontend") ~
      pathPrefix("") {
        get {
          getFromResource("frontend/index.html", ContentTypes.`text/html(UTF-8)`)
        }
      }
}
