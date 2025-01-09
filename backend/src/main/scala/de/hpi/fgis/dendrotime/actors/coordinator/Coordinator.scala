package de.hpi.fgis.dendrotime.actors.coordinator

import akka.actor.Cancellable
import akka.actor.typed.*
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers, StashBuffer}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.Communicator
import de.hpi.fgis.dendrotime.actors.clusterer.{Clusterer, ClustererProtocol}
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.{AdaptiveBatchingMixin, StrategyFactory, StrategyParameters, StrategyProtocol}
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.actors.worker.{Worker, WorkerProtocol}
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.Status
import de.hpi.fgis.dendrotime.structures.strategies.GrowableFCFSWorkGenerator

import scala.concurrent.duration.*


object Coordinator {

  type MessageType = Command | StrategyProtocol.StrategyCommand | StrategyProtocol.StrategyEvent
  sealed trait Command
  case object CancelProcessing extends Command
  case object ApproxFinished extends Command
  case object FullFinished extends Command
  case object ClusteringFinished extends Command
  case object Stop extends Command

  sealed trait TsLoadingCommand extends Command
  case class DatasetHasNTimeseries(n: StrategyProtocol.TsId) extends TsLoadingCommand
  case class NewTimeSeries(tsId: StrategyProtocol.TsId) extends TsLoadingCommand
  case class AllTimeSeriesLoaded(ids: Set[StrategyProtocol.TsId]) extends TsLoadingCommand
  case class FailedToLoadAllTimeSeries(cause: String) extends TsLoadingCommand

  sealed trait Response
  case class ProcessingStarted(id: Long, communicator: ActorRef[Communicator.Command]) extends Response
  case class ProcessingEnded(id: Long) extends Response
  case class ProcessingFailed(id: Long) extends Response
  case class ProcessingStatus(id: Long, status: Status) extends Response

  def apply(
             tsManager: ActorRef[TsmProtocol.Command],
             id: Long,
             dataset: Dataset,
             params: DendroTimeParams,
             reportTo: ActorRef[Response]): Behavior[MessageType] = Behaviors.setup { ctx =>
    val settings = Settings(ctx.system)
    val stashSize = settings.numberOfWorkers * 5

    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(StrategyProtocol.ReportStatus, settings.reportingInterval)
      Behaviors.withStash(stashSize) { stash =>
        new Coordinator(ctx, tsManager, id, dataset, params, reportTo, stash).start()
      }
    }
  }

  def props: Props = DispatcherSelector.fromConfig("dendrotime.coordinator-pinned-dispatcher")
}

private class Coordinator private[coordinator] (
                   ctx: ActorContext[Coordinator.MessageType],
                   tsManager: ActorRef[TsmProtocol.Command],
                   id: Long,
                   dataset: Dataset,
                   params: DendroTimeParams,
                   reportTo: ActorRef[Coordinator.Response],
                   stash: StashBuffer[Coordinator.MessageType]
                 ) extends AdaptiveBatchingMixin(ctx.system) {

  import Coordinator.*

  private val settings: Settings = Settings(ctx.system)
  private val communicator = ctx.spawn(Communicator(dataset, params), s"communicator-$id")
  ctx.watch(communicator)
  private val clusterer = ctx.spawn(
    Clusterer(dataset, params, ctx.self, tsManager, communicator),
    s"clusterer-$id",
    Clusterer.props
  )
  ctx.watch(clusterer)
  private val workers = {
    val router = Routers.pool(settings.numberOfWorkers)(Worker(dataset, params, tsManager, clusterer))
      .withRouteeProps(Worker.props)
      // broadcast supplier reference to all workers
      .withBroadcastPredicate{
        case WorkerProtocol.UseSupplier(_) => true
        case _ => false
      }
      .withRoundRobinRouting()
    ctx.spawn(router, s"worker-pool-$id")
  }
  private val fullStrategy = ctx.spawn(
    StrategyFactory(params.strategy, StrategyParameters(dataset, params, tsManager, clusterer), ctx.self),
    s"strategy-$id",
    StrategyFactory.props
  )
  ctx.watch(fullStrategy)
  private val workGenerator = GrowableFCFSWorkGenerator.empty[Int]


  def start(): Behavior[MessageType] = {
    tsManager ! TsmProtocol.GetTimeSeriesIds(Right(dataset), ctx.self)
    reportTo ! ProcessingStarted(id, communicator)
    reportTo ! ProcessingStatus(id, Status.Initializing)
    workers ! WorkerProtocol.UseSupplier(ctx.self.narrow[StrategyProtocol.StrategyCommand])

    initializing(Vector.empty)
  }

  private def initializing(tsIds: Seq[Int]): Behavior[MessageType] =
    Behaviors.receiveMessagePartial {
      case NewTimeSeries(tsId) =>
        ctx.log.debug("New time series ts-{} for dataset d-{} was loaded!", tsId, dataset.id)
        workGenerator += tsId
        if workGenerator.hasNext then
          stash.unstashAll(
            initializing(tsIds :+ tsId)
          )
        else
          initializing(tsIds :+ tsId)

      case DatasetHasNTimeseries(n) =>
        ctx.log.info("Dataset d-{} has {} time series, starting clusterer and SWITCHING TO LOADING STATE", dataset.id, n)
        workGenerator.sizeHint(n)
        // switch to loading state
        clusterer ! ClustererProtocol.Initialize(n)
        stash.unstashAll(loading(n, tsIds))

      case StrategyProtocol.DispatchWork(worker, time, size) if workGenerator.hasNext =>
        val batchSize = nextBatchSize(time, size)
        val work = workGenerator.nextBatch(batchSize)
        ctx.log.trace("Dispatching approx batch (n={}) ApproxQueue={}/{}", batchSize, workGenerator.index, workGenerator.sizeTuples)
        worker ! WorkerProtocol.CheckApproximate(work)
        initializing(tsIds)

      case m: StrategyProtocol.DispatchWork =>
        ctx.log.debug("Worker {} asked for work but there is none", m.worker)
        stash.stash(m)
        Behaviors.same

      case StrategyProtocol.ReportStatus =>
        ctx.log.info("[REPORT] Initializing, {} time series loaded, {}", tsIds.size, getBatchStats)
        Behaviors.same

      case FailedToLoadAllTimeSeries(_) =>
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped

      case CancelProcessing =>
        ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped
    }

  private def loading(nTimeseries: Int, tsIds: Seq[Int], approxFinished: Boolean = false):  Behavior[MessageType] =
    Behaviors.receiveMessagePartial {
      case NewTimeSeries(tsId) =>
        ctx.log.debug("New time series ts-{} for dataset d-{} was loaded!", tsId, dataset.id)
        workGenerator += tsId
        if workGenerator.hasNext then
          stash.unstashAll(
            loading(nTimeseries, tsIds :+ tsId)
          )
        else
          loading(nTimeseries, tsIds :+ tsId)

      case AllTimeSeriesLoaded(allTsIds) =>
        if allTsIds.size != tsIds.size || allTsIds.size != nTimeseries then
          throw new IllegalStateException(f"Not all time series were loaded (${tsIds.size} of $nTimeseries)")

        fullStrategy ! StrategyProtocol.AddTimeSeries(tsIds)

        // switch to approximating state
        ctx.log.info("All {} time series loaded for dataset d-{}, SWITCHING TO APPROXIMATING STATE", allTsIds.size, dataset.id)
        communicator ! Communicator.NewStatus(Status.Approximating)
        reportTo ! ProcessingStatus(id, Status.Approximating)
        if approxFinished then
          ctx.log.info("SWITCHING TO FULL STATE")
          communicator ! Communicator.NewStatus(Status.ComputingFullDistances)
          reportTo ! ProcessingStatus(id, Status.ComputingFullDistances)
        stash.unstashAll(running())

      case StrategyProtocol.DispatchWork(worker, time, size) if workGenerator.hasNext =>
        val batchSize = nextBatchSize(time, size)
        val work = workGenerator.nextBatch(batchSize)
        ctx.log.trace("Dispatching approx batch ({}) ApproxQueue={}/{}", batchSize, workGenerator.index, workGenerator.sizeTuples)
        worker ! WorkerProtocol.CheckApproximate(work)
        loading(nTimeseries, tsIds)

      case m: StrategyProtocol.DispatchWork =>
        ctx.log.debug("Worker {} asked for work but there is none", m.worker)
        stash.stash(m)
        Behaviors.same

      case ApproxFinished =>
        ctx.log.warn("Approximation finished before all TS where loaded, deferring state switch")
        loading(nTimeseries, tsIds, approxFinished = true)

      case StrategyProtocol.ReportStatus =>
        ctx.log.info("[REPORT] Loading, {}/{} time series loaded, {}", tsIds.size, nTimeseries, getBatchStats)
        Behaviors.same

      case FailedToLoadAllTimeSeries(_) =>
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped

      case CancelProcessing =>
        ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
        reportTo ! ProcessingFailed(id)
        Behaviors.stopped
    }

  private def running(fullOOW: Boolean = false): Behavior[MessageType] = Behaviors.receiveMessagePartial {
    case StrategyProtocol.DispatchWork(worker, time, size) if workGenerator.hasNext =>
      val batchSize = nextBatchSize(time, size)
      val work = workGenerator.nextBatch(batchSize)
      ctx.log.trace("Dispatching approx batch ({}) ApproxQueue={}/{}", batchSize, workGenerator.index, workGenerator.sizeTuples)
      worker ! WorkerProtocol.CheckApproximate(work)
      Behaviors.same

    case StrategyProtocol.DispatchWork(worker, _, _) => // if approxWorkQueue.noWork
      ctx.log.debug("Approx queue ({}/{}) ran out of work, switching to full strategy", workGenerator.remaining, workGenerator.sizeTuples)
      // do not forward batch runtimes!
      fullStrategy ! StrategyProtocol.DispatchWork(worker)
      // switch supplier for worker
      worker ! WorkerProtocol.UseSupplier(fullStrategy)
      Behaviors.same

    case StrategyProtocol.ReportStatus =>
      ctx.log.info("[REPORT] Running {}/{} remaining approx jobs, {}", workGenerator.remaining, workGenerator.sizeTuples, getBatchStats)
      Behaviors.same

    case ApproxFinished =>
      ctx.log.info("Approximation finished, SWITCHING TO FULL STATE")
      communicator ! Communicator.NewStatus(Status.ComputingFullDistances)
      reportTo ! ProcessingStatus(id, Status.ComputingFullDistances)
      Behaviors.same

    case StrategyProtocol.FullStrategyOutOfWork =>
      ctx.log.info("No more work to do, waiting for pending results, SWITCHING TO FINALIZING STATE!")
      communicator ! Communicator.NewStatus(Status.Finalizing)
      communicator ! Communicator.ProgressUpdate(Status.Finalizing, 50)
      reportTo ! ProcessingStatus(id, Status.Finalizing)
      running(fullOOW = true)

    case FullFinished =>
      if !fullOOW then
        ctx.log.warn("Received all full results before the message that full strategy is out of work, " +
          "SWITCHING TO FINALIZING STATE!")
        communicator ! Communicator.NewStatus(Status.Finalizing)
        communicator ! Communicator.ProgressUpdate(Status.Finalizing, 50)
        reportTo ! ProcessingStatus(id, Status.Finalizing)
      ctx.log.debug("Full strategy finished.")
      ctx.unwatch(clusterer)
      waitingForClustering(Cancellable.alreadyCancelled)

    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped
  }

  private def waitingForClustering(stopTimer: Cancellable): Behavior[MessageType] = Behaviors.receiveMessagePartial[MessageType] {
    case StrategyProtocol.FullStrategyOutOfWork | FullFinished =>
      // ignore, we are already waiting for clustering to finish
      Behaviors.same

    case ClusteringFinished =>
      communicator ! Communicator.ProgressUpdate(Status.Finalizing, 100)
      communicator ! Communicator.NewStatus(Status.Finished)
      reportTo ! ProcessingStatus(id, Status.Finished)
      ctx.log.info("FINISHED processing job {}", id)
      val stopTimer = ctx.scheduleOnce(30 seconds span, ctx.self, Stop)
      waitingForClustering(stopTimer)

    case StrategyProtocol.ReportStatus =>
      ctx.log.info("[REPORT] Waiting for clustering to finish")
      Behaviors.same

    case Stop =>
      // do not directly stop coordinator to let communicator store the final state to disk (if enabled)
      ctx.log.debug("Shutting down communicator")
      ctx.stop(communicator)
      stopTimer.cancel()
      Behaviors.same

    case CancelProcessing =>
      ctx.log.warn("Cancelling processing of dataset d-{} on request", dataset.id)
      reportTo ! ProcessingFailed(id)
      stopTimer.cancel()
      Behaviors.stopped

  } receiveSignal {
    case (_, Terminated(`communicator`)) =>
      ctx.log.debug("Communicator is finished, shutting down coordinator!")
      reportTo ! ProcessingEnded(id)
      Behaviors.stopped
  }
}
