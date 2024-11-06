package de.hpi.fgis.dendrotime

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import de.hpi.fgis.dendrotime.actors.{DatasetRegistry, Scheduler}
import de.hpi.fgis.dendrotime.api.ApiRoutesProvider

import scala.util.{Failure, Success}


object Server extends ApiRoutesProvider {

  // Actor message protocol
  sealed trait Message
  private final case class StartFailed(cause: Throwable) extends Message
  private final case class Started(binding: Http.ServerBinding) extends Message
  case object Stop extends Message
  private case object Stopped extends Message

  // behavior
  def apply(assets: Route): Behavior[Message] = Behaviors.setup { ctx =>
    given system: ActorSystem[?] = ctx.system

    val settings = Settings(system)

    // start actors
    val scheduler = ctx.spawn(Scheduler(), "scheduler")
    ctx.watch(scheduler)
    val datasetRegistry = ctx.spawn(DatasetRegistry(), "dataset-registry")
    ctx.watch(datasetRegistry)

    // start server
    val routes = createApiRoutes(
      datasetServiceRoutes(datasetRegistry),
      jobServiceRoutes(scheduler)
    ) ~ assets
    val bindingFuture = Http().newServerAt(settings.host, settings.port).bind(routes)
    ctx.pipeToSelf(bindingFuture) {
      case Success(binding) => Started(binding)
      case Failure(ex) => StartFailed(ex)
    }

    starting(wasStopped = false)
  }

  private def starting(wasStopped: Boolean): Behavior[Message] = Behaviors.receive[Message] {
    case (_, StartFailed(cause)) =>
      throw RuntimeException("Server could not start", cause)
    case (ctx, Started(binding)) =>
      ctx.log.info("Server started at https://{}:{}/", binding.localAddress.getHostString, binding.localAddress.getPort)
      // when started, we go to running state and handle stop messages
      if (wasStopped) ctx.self ! Stop
      running(binding)
    case (_, Stop | Stopped) =>
      // we got a stop message but haven't completed starting yet,
      // we cannot stop until starting has completed
      starting(wasStopped = true)
  }

  private def running(binding: Http.ServerBinding): Behavior[Message] = Behaviors.receivePartial[Message] {
    case (ctx, Stopped) =>
      ctx.log.info("... done. Terminating system!")
      Behaviors.stopped
    case (ctx, Stop) =>
      ctx.log.info("Stopping server ...")
      val unbindingFuture = binding.unbind()
      ctx.pipeToSelf(unbindingFuture)(_ => Stopped)
      Behaviors.same
  }.receiveSignal {
    case (ctx, PostStop) =>
      ctx.log.info("Stopping server ...")
      val unbindingFuture = binding.unbind()
      ctx.pipeToSelf(unbindingFuture)(_ => Stopped)
      Behaviors.same
  }
}
