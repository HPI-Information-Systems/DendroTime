package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.DispatchWork
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol.GetTimeSeriesResponse
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.{LabeledTimeSeries, TimeSeries}
import org.apache.commons.math3.util.FastMath

object Worker {
  def apply(dataset: Dataset,
            params: DendroTimeParams,
            tsManager: ActorRef[TsmProtocol.Command],
            clusterer: ActorRef[ClustererProtocol.Command],
           ): Behavior[WorkerProtocol.Command] = Behaviors.setup { ctx =>
    Behaviors.withStash(10) { stash =>
      new Worker(WorkerContext(ctx, stash, tsManager, clusterer), dataset.id, params).start()
    }
  }

  def props: Props = DispatcherSelector.fromConfig("dendrotime.worker-dispatcher")
}

private class Worker private(ctx: WorkerContext, datasetId: Int, params: DendroTimeParams) {

  import WorkerProtocol.*

  private val getTSAdapter = ctx.context.messageAdapter[GetTimeSeriesResponse](m => TimeSeriesLoaded(m.timeseries))
  import ctx.settings.Distances.given
  private val distanceMetric = params.distance

  ctx.tsManager ! TsmProtocol.GetTimeSeries(datasetId, getTSAdapter)

  private def start(supplier: Option[ActorRef[DispatchWork]] = None,
                    tsMap: Option[Map[Int, LabeledTimeSeries]] = None): Behavior[Command] = Behaviors.receiveMessagePartial{
    case UseSupplier(supplier) =>
      supplier ! DispatchWork(ctx.self)
      if tsMap.isDefined then
        ctx.unstashAll(idle(supplier, tsMap.get))
      else
        start(Some(supplier), tsMap)

    case TimeSeriesLoaded(ts) =>
      if supplier.isDefined then
        ctx.unstashAll(idle(supplier.get, ts))
      else
        start(supplier, Some(ts))

    case m =>
      ctx.stash(m)
      Behaviors.same
  }

  private def idle(workSupplier: ActorRef[DispatchWork],
                   ts: Map[Int, LabeledTimeSeries],
                   sendBatchStatistics: Boolean = true): Behavior[Command] = Behaviors.receiveMessagePartial {
    case UseSupplier(supplier) =>
      ctx.log.debug("Switching supplier to {}", supplier)
      idle(supplier, ts, sendBatchStatistics = false)

    case job: CheckCommand =>
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
        workSupplier ! DispatchWork(ctx.self, lastJobDuration = duration, lastBatchSize = job.size)
      else
        workSupplier ! DispatchWork(ctx.self)

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
      idle(workSupplier, ts)
  }

  @inline
  private def checkApproximate(ts1: TimeSeries, ts2: TimeSeries): (Int, Int, Double) = {
    val snippetSize = ctx.settings.Distances.approxLength
    val scale = FastMath.max(ts1.data.length, ts2.data.length) / snippetSize
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
