package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.*
import akka.actor.typed.scaladsl.Behaviors
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.DispatchWork
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol.GetTimeSeriesResponse
import de.hpi.fgis.dendrotime.io.TimeSeries
import de.hpi.fgis.dendrotime.io.TimeSeries.LabeledTimeSeries
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
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

  private def start(
                     supplier: Option[ActorRef[DispatchWork]] = None,
                     tsMap: Option[IndexedSeq[LabeledTimeSeries]] = None
                   ): Behavior[Command] = Behaviors.receiveMessagePartial {
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
                   ts: IndexedSeq[LabeledTimeSeries],
                   sendBatchStatistics: Boolean = true): Behavior[Command] = Behaviors.receiveMessagePartial[Command] {
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
        val dist = if job.isApproximate then checkApproximate(ts(t1), ts(t2)) else checkFull(ts(t1), ts(t2))
        tas(k) = t1
        tbs(k) = t2
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
          val (a, b, c) = computeEstimates((m1, m2), ids1, ids2, dists.head)
          if a.nonEmpty then
            ctx.clusterer ! ClustererProtocol.EstimatedDistance(a, b, c)
        case j if j.isApproximate =>
          ctx.clusterer ! ClustererProtocol.ApproximateDistance(tas, tbs, dists)
        case _ =>
          ctx.clusterer ! ClustererProtocol.FullDistance(tas, tbs, dists)
      }
      idle(workSupplier, ts)
  } receiveSignal {
    case (_, PostStop) =>
      distanceMetric match {
        case d: AutoCloseable => d.close()
        case _ =>
      }
      Behaviors.same
  }

  @inline
  private def computeEstimates(medoids: (Int, Int), ids1: Seq[Int], ids2: Seq[Int], dist: Double): (Array[Int], Array[Int], Array[Double]) = {
    val (m1, m2) = medoids
    val n = ids1.length * ids2.length - 1
    val id1 = Array.ofDim[Int](n)
    val id2 = Array.ofDim[Int](n)
    val dists = Array.ofDim[Double](n)

    var k = 0
    for
      i <- ids1
      j <- ids2
      if !(i == m1 && j == m2)
    do
      id1(k) = math.min(i, j)
      id2(k) = math.max(i, j)
      dists(k) = dist
      k += 1
    (id1, id2, dists)
  }

  @inline
  private def checkApproximate(ts1: TimeSeries, ts2: TimeSeries): Double = {
    val snippetSize = ctx.settings.Distances.approxLength
    val scale = FastMath.max(ts1.data.length, ts2.data.length) / snippetSize
    val ts1Center = ts1.data.length / 2
    val ts2Center = ts2.data.length / 2
    val dist = distanceMetric(
      ts1.data.slice(ts1Center - snippetSize / 2, ts1Center + snippetSize / 2),
      ts2.data.slice(ts2Center - snippetSize / 2, ts2Center + snippetSize / 2)
    ) * scale
    dist
  }

  @inline
  private def checkFull(ts1: TimeSeries, ts2: TimeSeries): Double = distanceMetric(ts1.data, ts2.data)
}
