package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.{Communicator, TimeSeriesManager}
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.TimeSeries

object Worker {
  sealed trait Command
  case class CheckApproximate(t1: Long, t2: Long) extends Command
  case class CheckFull(t1: Long, t2: Long) extends Command
  private case class GetTimeSeriesResponse(msg: TimeSeriesManager.GetTimeSeriesResponse) extends Command

  def apply(tsManager: ActorRef[TimeSeriesManager.Command],
            coordinator: ActorRef[Coordinator.Command],
            communicator: ActorRef[Communicator.Command],
            params: DendroTimeParams): Behavior[Command] = Behaviors.setup { ctx =>
    new Worker(WorkerContext(ctx, tsManager, coordinator, communicator), params).start()
  }
}

private class Worker private(ctx: WorkerContext, params: DendroTimeParams) {

  import Worker.*

  private val getTSAdapter = ctx.context.messageAdapter(GetTimeSeriesResponse.apply)

  private def start(): Behavior[Command] =
    ctx.coordinator ! Coordinator.DispatchWork(ctx.context.self)
    idle

  private def idle: Behavior[Command] = Behaviors.receiveMessagePartial {
    case CheckApproximate(t1, t2) =>
      ctx.tsManager ! TimeSeriesManager.GetTimeSeries(t1, getTSAdapter)
      ctx.tsManager ! TimeSeriesManager.GetTimeSeries(t2, getTSAdapter)
      waitingForTs(Map.empty, t1, t2)
    case CheckFull(t1, t2) =>
      ctx.tsManager ! TimeSeriesManager.GetTimeSeries(t1, getTSAdapter)
      ctx.tsManager ! TimeSeriesManager.GetTimeSeries(t2, getTSAdapter)
      waitingForTs(Map.empty, t1, t2, full = true)
  }

  private def waitingForTs(tsMap: Map[Long, TimeSeries],
                           t1: Long,
                           t2: Long,
                           full: Boolean = false): Behavior[Command] = Behaviors.receiveMessagePartial {
    case GetTimeSeriesResponse(TimeSeriesManager.TimeSeriesFound(ts)) =>
      val newTs = tsMap + (ts.id -> ts)
      if (newTs.size == 2)
        if full then
          checkFull(newTs(t1), newTs(t2))
        else
          checkApproximate(newTs(t1), newTs(t2))
        ctx.coordinator ! Coordinator.DispatchWork(ctx.context.self)
        idle
      else
        waitingForTs(newTs, t1, t2, full)
    case GetTimeSeriesResponse(TimeSeriesManager.TimeSeriesNotFound(id)) =>
      ctx.context.log.error("Time series ts-{} not found", id)
      // report failure to coordinator?
      Behaviors.stopped
  }

  private def checkApproximate(ts1: TimeSeries, ts2: TimeSeries): Unit = {
    val dist = params.metric(ts1.data.slice(0, params.approxLength), ts2.data.slice(0, params.approxLength))
    ctx.coordinator ! Coordinator.ApproximationResult(ts1.id, ts2.id, ts1.idx, ts2.idx, dist)
  }

  private def checkFull(ts1: TimeSeries, ts2: TimeSeries): Unit = {
    val dist = params.metric(ts1.data, ts2.data)
    ctx.coordinator ! Coordinator.FullResult(ts1.id, ts2.id, ts1.idx, ts2.idx, dist)
  }
}
