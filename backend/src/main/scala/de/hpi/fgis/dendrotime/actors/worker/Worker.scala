package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer
import de.hpi.fgis.dendrotime.actors.{Communicator, TimeSeriesManager}
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.DispatchWork
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.Status
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.TimeSeries

object Worker {
  sealed trait Command
  case class UseSupplier(supplier: ActorRef[DispatchWork]) extends Command
  case class CheckApproximate(t1: Long, t2: Long) extends Command
  case class CheckFull(t1: Long, t2: Long) extends Command
  private case class GetTimeSeriesResponse(msg: TimeSeriesManager.GetTimeSeriesResponse) extends Command

  def apply(tsManager: ActorRef[TimeSeriesManager.Command],
            clusterer: ActorRef[Clusterer.Command],
            params: DendroTimeParams): Behavior[Command] = Behaviors.setup { ctx =>
    new Worker(WorkerContext(ctx, tsManager, clusterer), params).start()
  }

  def props: Props = DispatcherSelector.fromConfig("dendrotime.worker-dispatcher")
}

private class Worker private(ctx: WorkerContext, params: DendroTimeParams) {

  import Worker.*

  private val settings = Settings(ctx.context.system)
  private val getTSAdapter = ctx.context.messageAdapter(GetTimeSeriesResponse.apply)
  import settings.Distances.given
  private val distanceMetric = params.metric

  private def start(): Behavior[Command] = Behaviors.receiveMessagePartial{
    case UseSupplier(supplier) =>
      supplier ! DispatchWork(ctx.context.self)
      idle(supplier)
  }

  private def idle(workSupplier: ActorRef[DispatchWork]): Behavior[Command] = Behaviors.receiveMessagePartial {
    case UseSupplier(supplier) =>
      ctx.context.log.debug("Switching supplier to {}", supplier)
      idle(supplier)

    case CheckApproximate(t1, t2) =>
      ctx.tsManager ! TimeSeriesManager.GetTimeSeries(t1, t2, getTSAdapter)
      waitingForTs(workSupplier)

    case CheckFull(t1, t2) =>
      ctx.tsManager ! TimeSeriesManager.GetTimeSeries(t1, t2, getTSAdapter)
      waitingForTs(workSupplier, full = true)
  }

  private def waitingForTs(workSupplier: ActorRef[DispatchWork],
                           full: Boolean = false): Behavior[Command] = Behaviors.receiveMessagePartial {
    case UseSupplier(supplier) =>
      ctx.context.log.debug("Switching supplier to {}", supplier)
      waitingForTs(supplier, full)

    case GetTimeSeriesResponse(TimeSeriesManager.TimeSeriesFound(t1, t2)) =>
      if full then
        checkFull(t1, t2)
      else
        checkApproximate(t1, t2)
      workSupplier ! DispatchWork(ctx.context.self)
      idle(workSupplier)

    // FIXME: this case does not happen in regular operation (only reason would be a bug in my code)
    case GetTimeSeriesResponse(TimeSeriesManager.TimeSeriesNotFound(ids)) =>
      ctx.context.log.error("Time series {} not found", ids)
      // report failure to coordinator?
      Behaviors.stopped
  }

  @inline
  private def checkApproximate(ts1: TimeSeries, ts2: TimeSeries): Unit = {
    val ts1Center = ts1.data.length / 2
    val ts2Center = ts2.data.length / 2
    val dist = distanceMetric(
      ts1.data.slice(Math.max(0, ts1Center - params.approxLength/2), Math.min(ts1Center + params.approxLength/2, ts1.data.length)),
      ts2.data.slice(Math.max(0, ts2Center - params.approxLength/2), Math.min(ts2Center + params.approxLength/2, ts2.data.length)),
    )
    ctx.clusterer ! Clusterer.ApproximateDistance(ts1.idx, ts2.idx, dist)
  }

  @inline
  private def checkFull(ts1: TimeSeries, ts2: TimeSeries): Unit = {
    val dist = distanceMetric(ts1.data, ts2.data)
    ctx.clusterer ! Clusterer.FullDistance(ts1.idx, ts2.idx, dist)
  }
}
