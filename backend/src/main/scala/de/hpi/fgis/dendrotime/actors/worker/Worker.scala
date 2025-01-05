package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.DispatchWork
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.TimeSeries

object Worker {
  def apply(tsManager: ActorRef[TsmProtocol.Command],
            clusterer: ActorRef[ClustererProtocol.Command],
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
  private val distanceMetric = params.distance

  private def start(): Behavior[Command] = Behaviors.receiveMessagePartial{
    case UseSupplier(supplier) =>
      supplier ! DispatchWork(ctx.context.self)
      idle(supplier)
  }

  private def idle(workSupplier: ActorRef[DispatchWork]): Behavior[Command] = Behaviors.receiveMessagePartial {
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
      // compute distances
      val start = System.nanoTime()
      val tas = Array.ofDim[Int](job.size)
      val tbs = Array.ofDim[Int](job.size)
      val dists = Array.ofDim[Double](job.size)
      var k = 0
      while job.hasNext do
        val (t1, t2) = job.next()
        val (idx1, idx2, dist) = if job.isApproximate then checkApproximate(ts(t1), ts(t2)) else checkFull(ts(t1), ts(t2))
        tas(k) = idx1
        tbs(k) = idx2
        dists(k) = dist
        k += 1
      val duration = System.nanoTime() - start

      // send batch statistics to coordinator and request more work
      if sendBatchStatistics then
        workSupplier ! DispatchWork(ctx.context.self, lastJobDuration = duration, lastBatchSize = job.size)
      else
        workSupplier ! DispatchWork(ctx.context.self)

      // send distances to clusterer
      job match {
        case CheckMedoids(m1, m2, ids1, ids2, justBroadcast) =>
          if !justBroadcast then
            ctx.clusterer ! ClustererProtocol.FullDistance(tas, tbs, dists)
          // medoid distance estimates the distance between all other inter-cluster pairs:
          val estimated = for {
            idx1 <- ids1.map(ts(_).idx)
            idx2 <- ids2.map(ts(_).idx)
            if idx1 != m1 && idx2 != m2 
          } yield (idx1, idx2, dists.head)
          val (a, b, c) = estimated.unzip3
          if estimated.nonEmpty then
            ctx.clusterer ! ClustererProtocol.EstimatedDistance(a, b, c)
        case j if j.isApproximate =>
          ctx.clusterer ! ClustererProtocol.ApproximateDistance(tas, tbs, dists)
        case _ =>
          ctx.clusterer ! ClustererProtocol.FullDistance(tas, tbs, dists)
      }
      idle(workSupplier)

    // FIXME: this case does not happen in regular operation (only reason would be a bug in my code)
    case GetTimeSeriesResponse(TsmProtocol.TimeSeriesNotFound) =>
      ctx.context.log.error("Time series for job {} not found", job)
      // report failure to coordinator?
      Behaviors.stopped
  }

  @inline
  private def checkApproximate(ts1: TimeSeries, ts2: TimeSeries): (Int, Int, Double) = {
    val snippetSize = settings.Distances.approxLength
    val scale = Math.max(ts1.data.length, ts2.data.length) / snippetSize
    val ts1Center = ts1.data.length / 2
    val ts2Center = ts2.data.length / 2
    val dist = distanceMetric(
      ts1.data.slice(ts1Center - snippetSize / 2, ts1Center + snippetSize / 2),
      ts2.data.slice(ts2Center - snippetSize / 2, ts2Center + snippetSize / 2)
    ) * scale
    (ts1.idx, ts2.idx, dist)
  }

  @inline
  private def checkFull(ts1: TimeSeries, ts2: TimeSeries): (Int, Int, Double) = {
    val dist = distanceMetric(ts1.data, ts2.data)
    (ts1.idx, ts2.idx, dist)
  }
}
