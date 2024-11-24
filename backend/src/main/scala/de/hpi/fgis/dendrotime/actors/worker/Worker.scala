package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.DispatchWork
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.TimeSeries

object Worker {
  def apply(tsManager: ActorRef[TsmProtocol.Command],
            clusterer: ActorRef[Clusterer.Command],
            params: DendroTimeParams): Behavior[WorkerProtocol.Command] = Behaviors.setup { ctx =>
    new Worker(WorkerContext(ctx, tsManager, clusterer), params).start()
  }

  def props: Props = DispatcherSelector.fromConfig("dendrotime.worker-dispatcher")
}

private class Worker private(ctx: WorkerContext, params: DendroTimeParams) {

  import WorkerProtocol.*

  private val settings = Settings(ctx.context.system)
  private val getTSAdapter = ctx.context.messageAdapter(GetTimeSeriesResponse.apply)
  import settings.Distances.given
  private val distanceMetric = params.metric

  private def start(): Behavior[Command] = Behaviors.receiveMessagePartial{
    case UseSupplier(supplier) =>
      supplier ! DispatchWork(ctx.context.self)
      idle(supplier)
  }

  private def idle(workSupplier: ActorRef[DispatchWork], job: Option[CheckCommand] = None): Behavior[Command] = Behaviors.receiveMessagePartial {
    case UseSupplier(supplier) =>
      ctx.context.log.debug("Switching supplier to {}", supplier)
      idle(supplier)

    case m: CheckCommand =>
      ctx.tsManager ! m.tsRequest(getTSAdapter)
      waitingForTs(workSupplier, m)
  }

  private def waitingForTs(workSupplier: ActorRef[DispatchWork],
                           job: CheckCommand,
                           sendBatchStatistics: Boolean = true): Behavior[Command] = Behaviors.receiveMessagePartial {
    case UseSupplier(supplier) =>
      ctx.context.log.debug("Switching supplier to {}", supplier)
      waitingForTs(supplier, job, sendBatchStatistics = false)

    case GetTimeSeriesResponse(ts: TsmProtocol.TimeSeriesFound) =>
      val start = System.nanoTime()
      if job.isApproximate then
        while job.hasNext do
          val (t1, t2) = job.next()
          checkApproximate(ts(t1), ts(t2))
      else
        while job.hasNext do
          val (t1, t2) = job.next()
          checkFull(ts(t1), ts(t2))
      val duration = System.nanoTime() - start
      if sendBatchStatistics then
        workSupplier ! DispatchWork(ctx.context.self, lastJobDuration = duration, lastBatchSize = job.size)
      else
        workSupplier ! DispatchWork(ctx.context.self)
      idle(workSupplier)

    // FIXME: this case does not happen in regular operation (only reason would be a bug in my code)
    case GetTimeSeriesResponse(TsmProtocol.TimeSeriesNotFound) =>
      ctx.context.log.error("Time series for job {} not found", job)
      // report failure to coordinator?
      Behaviors.stopped
  }

  @inline
  private def checkApproximate(ts1: TimeSeries, ts2: TimeSeries): Unit = {
    val ts1Center = ts1.data.length / 2
    val ts2Center = ts2.data.length / 2
    val dist = distanceMetric(
      ts1.data.slice(ts1Center - params.approxLength/2, ts1Center + params.approxLength/2),
      ts2.data.slice(ts2Center - params.approxLength/2, ts2Center + params.approxLength/2),
    )
    ctx.clusterer ! Clusterer.ApproximateDistance(ts1.idx, ts2.idx, dist)
  }

  @inline
  private def checkFull(ts1: TimeSeries, ts2: TimeSeries): Unit = {
    val dist = distanceMetric(ts1.data, ts2.data)
    ctx.clusterer ! Clusterer.FullDistance(ts1.idx, ts2.idx, dist)
  }
}
