package de.hpi.fgis.dendrotime.actors.coordinator

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, SupervisorStrategy}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.{ApproxFCFSStrategy, Strategy}
import de.hpi.fgis.dendrotime.actors.worker.Worker
import de.hpi.fgis.dendrotime.actors.{Communicator, TimeSeriesManager}
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.Status

import scala.concurrent.duration.*


object Coordinator {

  sealed trait Command
  case object CancelProcessing extends Command
  case object ClusteringFinished extends Command
  case object Stop extends Command

  case object ApproxOutOfWork extends Command
  case object ApproxStrategyFinished extends Command
  case object FullOutOfWork extends Command
  case object FullStrategyFinished extends Command

  sealed trait TsLoadingCommand extends Command
  case class DatasetHasNTimeseries(n: Int) extends TsLoadingCommand
  case class NewTimeSeries(tsId: Long) extends TsLoadingCommand
  case class AllTimeSeriesLoaded(ids: Set[Long]) extends TsLoadingCommand
  case class FailedToLoadAllTimeSeries(cause: String) extends TsLoadingCommand

  sealed trait Response
  case class ProcessingStarted(id: Long, communicator: ActorRef[Communicator.Command]) extends Response
  case class ProcessingEnded(id: Long) extends Response
  case class ProcessingFailed(id: Long) extends Response
  case class ProcessingStatus(id: Long, status: Status) extends Response

  def apply(tsManager: ActorRef[TimeSeriesManager.Command],
            id: Long,
            dataset: Dataset,
            params: DendroTimeParams,
            reportTo: ActorRef[Response]): Behavior[Command] = Behaviors.setup { ctx =>
    new Coordinator(ctx, tsManager, id, dataset, params, reportTo).start()
  }
}

private class Coordinator private (
                   ctx: ActorContext[Coordinator.Command],
                   tsManager: ActorRef[TimeSeriesManager.Command],
                   id: Long,
                   dataset: Dataset,
                   params: DendroTimeParams,
                   reportTo: ActorRef[Coordinator.Response]
                 ) {

  import Coordinator.*

  private val settings = Settings(ctx.system)
  private val communicator = ctx.spawn(Communicator(dataset.id), s"communicator-$id")
  ctx.watch(communicator)
  private val clusterer = ctx.spawn(Clusterer(tsManager, communicator, dataset, params), s"clusterer-$id")
  ctx.watch(clusterer)
  private val workers = {
    val supervisedWorkerBehavior = Behaviors
      .supervise(Worker(tsManager, clusterer, params))
      .onFailure[Exception](SupervisorStrategy.restart)
    val router = Routers.pool(settings.numberOfWorkers)(supervisedWorkerBehavior)
      .withRouteeProps(DispatcherSelector.blocking())
      // broadcast new supplier reference to all workers
      .withBroadcastPredicate {
        case Worker.UseSupplier(_) => true
        case _ => false
      }
      .withRoundRobinRouting()
    ctx.spawn(router, s"worker-pool-$id")
  }
  private val fullStrategy = ctx.spawn(
    Strategy.fcfs(params, tsManager, ctx.self, communicator, clusterer),
    s"full-fcfs-strategy-$id"
  )
  ctx.watch(fullStrategy)
  private val approxStrategy = ctx.spawn(
    ApproxFCFSStrategy(fullStrategy, ctx.self, communicator),
    s"approx-fcfs-strategy-$id"
  )
  ctx.watch(approxStrategy)


  private def start(): Behavior[Command] = {
    tsManager ! TimeSeriesManager.GetTimeSeriesIds(Right(dataset), ctx.self)
    reportTo ! ProcessingStarted(id, communicator)
    workers ! Worker.UseSupplier(approxStrategy)
    initializing()
  }

  private def initializing(nTimeseries: Option[Int] = None): Behavior[Command] = Behaviors.receiveMessagePartial {
    case NewTimeSeries(tsId) =>
      ctx.log.info("New time series ts-{} for dataset d-{} was loaded!", tsId, dataset.id)
      approxStrategy ! Strategy.AddTimeSeries(Seq(tsId))
      Behaviors.same

    case DatasetHasNTimeseries(n) =>
      ctx.log.info("Dataset d-{} has {} time series, starting clusterer and switching to loading state", dataset.id, n)
      // switch to loading state
      clusterer ! Clusterer.Initialize(n)
      initializing(Some(n))

    case AllTimeSeriesLoaded(allTsIds) =>
      ctx.log.info("All {} time series loaded for dataset d-{}", allTsIds.size, dataset.id)
      // -- FIXME: this case does not happen in regular operation (only reason would be a bug in my code)
      if nTimeseries.exists(_ != allTsIds.size) then
        throw new IllegalStateException(f"Not all time series were loaded (${allTsIds.size} of $nTimeseries)")
      // --

      // switch to approximating state
      communicator ! Communicator.NewStatus(Status.Approximating)
      fullStrategy ! Strategy.AddTimeSeries(allTsIds.toSeq)
      running()

    case FailedToLoadAllTimeSeries(_) =>
      ctx.log.error("Failed to load time series for dataset d-{}", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped

    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped
  }

  private def running(): Behavior[Command] = Behaviors.receiveMessagePartial {
    case ApproxOutOfWork =>
      ctx.log.info("No more approx jobs, waiting for remaining results!")
      workers ! Worker.UseSupplier(fullStrategy)
      Behaviors.same

    case ApproxStrategyFinished =>
      ctx.log.info("Changing to full")
      communicator ! Communicator.NewStatus(Status.ComputingFullDistances)
      Behaviors.same

    case FullOutOfWork =>
      ctx.log.info("No more work to do, waiting for remaining results and clustering!")
      communicator ! Communicator.NewStatus(Status.Finalizing)
      communicator ! Communicator.ProgressUpdate(Status.Finalizing, 50)
      Behaviors.same

    case FullStrategyFinished =>
      ctx.log.info("Finished computing full distances, waiting for clustering!")
      clusterer ! Clusterer.ReportFinished(ctx.self)
      ctx.unwatch(clusterer)
      reportTo ! ProcessingStatus(id, Status.Finished)
      ctx.scheduleOnce(30 seconds span, ctx.self, Stop)
      Behaviors.same

    case ClusteringFinished =>
      communicator ! Communicator.ProgressUpdate(Status.Finalizing, 100)
      communicator ! Communicator.NewStatus(Status.Finished)
      ctx.log.info("Finished processing job {}", id)
      Behaviors.same

    case Stop =>
      ctx.log.info("Shutting down coordinator")
      reportTo ! ProcessingEnded(id)
      Behaviors.stopped

    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped
  }
}
