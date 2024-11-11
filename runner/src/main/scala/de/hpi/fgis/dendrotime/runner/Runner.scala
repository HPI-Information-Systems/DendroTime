package de.hpi.fgis.dendrotime.runner

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.TimeSeriesManager
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator.Response
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.Status

object Runner {
  trait Command
  case class ProcessDataset(dataset: Dataset, params: DendroTimeParams) extends Command
  case object Stop extends Command

  type MessageType = Command | Coordinator.Response

  def create(): Behavior[MessageType] = Behaviors.setup { ctx =>
    new Runner(ctx).start()
  }
}

class Runner(ctx: ActorContext[Runner.MessageType]) {

  import Runner.*

  private val settings = Settings(ctx.system)
  private val tsManager = ctx.spawn(TimeSeriesManager(), "time-series-manager")
  ctx.watch(tsManager)

  def start(): Behavior[MessageType] = Behaviors.receiveMessage {
    case ProcessDataset(dataset, params) =>
      ctx.log.info(s"Processing dataset $dataset with parameters $params")
      val startTime = System.currentTimeMillis()
      val coordinator = ctx.spawn(
        Coordinator(tsManager, 0, dataset, params, ctx.self),
        "coordinator",
        Coordinator.props
      )
      ctx.watch(coordinator)
      waitingForFinish(startTime, coordinator)

    case Stop =>
      ctx.log.info("Stopping runner")
      // automatically stops all children
      Behaviors.stopped

    case m =>
      ctx.log.error(s"Unexpected message: $m")
      Behaviors.stopped
  }

  private def waitingForFinish(start: Long, coordinator: ActorRef[Coordinator.Command]): Behavior[MessageType] =
    Behaviors.receiveMessage[MessageType] {
      case Coordinator.ProcessingStarted(_, _) =>
        // ignore
        Behaviors.same

      case Coordinator.ProcessingStatus(_, Status.Finished) =>
        val duration = System.currentTimeMillis() - start
        println(s"Processing finished in $duration ms")
        println(s"Result is stored in ${settings.resultsPath}")
        coordinator ! Coordinator.Stop
        ctx.unwatch(coordinator)
        Behaviors.same

      case Coordinator.ProcessingStatus(_, _) =>
        // ignore
        Behaviors.same

      case Coordinator.ProcessingEnded(_) =>
        ctx.log.info("Process finished, shutting down system.")
        Behaviors.stopped

      case Coordinator.ProcessingFailed(_) =>
        ctx.log.error("Processing failed")
        Behaviors.stopped

      case Stop =>
        ctx.log.info("Stopping runner")
        Behaviors.stopped

      case m =>
        ctx.log.error(s"Unexpected message: $m")
        Behaviors.stopped
    }.receiveSignal{
      case (_, Terminated(`tsManager`)) =>
        ctx.log.error(s"TimeSeriesManager died unexpectedly!")
        Behaviors.stopped
    }
}
