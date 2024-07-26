package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.{Communicator, TimeSeriesManager}
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

object Worker {
  sealed trait Command
  case class CheckApproximate(t1: Long, t2: Long) extends Command
  private case class GetTimeSeriesResponse(msg: TimeSeriesManager.GetTimeSeriesResponse) extends Command

  def apply(tsManager: ActorRef[TimeSeriesManager.Command],
            coordinator: ActorRef[Coordinator.Command],
            communicator: ActorRef[Communicator.Command],
            datasetId: Int): Behavior[Command] = Behaviors.setup { ctx =>
    new Worker(WorkerContext(ctx, tsManager, coordinator, communicator), datasetId).start()
  }
}

private class Worker private(ctx: WorkerContext, datasetId: Int) {

  import Worker.*
  
  private val getTSAdapter = ctx.context.messageAdapter(GetTimeSeriesResponse.apply)
  private val distance = Settings(ctx.context.system).distance

  private def start(): Behavior[Command] =
    ctx.coordinator ! Coordinator.DispatchWork(ctx.context.self)
    idle
  
  private def idle: Behavior[Command] = Behaviors.receiveMessagePartial {
    case CheckApproximate(t1, t2) =>
      ctx.tsManager ! TimeSeriesManager.GetTimeSeries(t1, getTSAdapter)
      ctx.tsManager ! TimeSeriesManager.GetTimeSeries(t2, getTSAdapter)
      waitingForTs(Map.empty, t1, t2)
  }
  
  private def waitingForTs(tsMap: Map[Long, LabeledTimeSeries], t1: Long, t2: Long): Behavior[Command] = Behaviors.receiveMessagePartial {
    case GetTimeSeriesResponse(TimeSeriesManager.TimeSeriesFound(ts)) =>
        val newTs = tsMap + (ts.id -> ts)
        if (newTs.size == 2)
          checkApproximate(newTs(t1), newTs(t2))
          ctx.coordinator ! Coordinator.DispatchWork(ctx.context.self)
          idle
        else
          waitingForTs(newTs, t1, t2)
    case GetTimeSeriesResponse(TimeSeriesManager.TimeSeriesNotFound(id)) =>
        ctx.context.log.error("Time series ts-{} not found", id)
        // report failure to coordinator?
        Behaviors.stopped
  }
  
  private def checkApproximate(ts1: LabeledTimeSeries, ts2: LabeledTimeSeries): Unit = {
    val dist = distance(ts1.data.slice(0, 10), ts2.data.slice(0, 10))
    Thread.sleep(100)
    ctx.coordinator ! Coordinator.ApproximationResult(ts1.id, ts2.id, dist)
  }
}
