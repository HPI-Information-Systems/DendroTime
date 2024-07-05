package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset

import scala.runtime.Nothing$


object Coordinator {

  sealed trait Command
  case class GetStatus(replyTo: ActorRef[ProcessingStatus]) extends Command
  case class NewTimeSeries(datasetId: Int, tsId: Long) extends Command
  private case class LoadDatasetResponse(msg: DatasetLoader.Response) extends Command

  sealed trait Response
  case class ProcessingEnded(id: Long) extends Response
  case class ProcessingFailed(id: Long) extends Response
  case class ProcessingStatus(id: Long, status: State) extends Response

  sealed trait State
  case object Initializing extends State
  case object Approximating extends State
  case object ComputingFullDistances extends State
  case object Finalizing extends State

  def apply(
             tsManager: ActorRef[TimeSeriesManager.Command],
             id: Long,
             dataset: Dataset,
             reportTo: ActorRef[Response]): Behavior[Command] = Behaviors.setup { ctx =>
    new Coordinator(ctx, tsManager, id, dataset, reportTo).start()
  }
}

class Coordinator(
                   ctx: ActorContext[Coordinator.Command],
                   tsManager: ActorRef[TimeSeriesManager.Command],
                   id: Long,
                   dataset: Dataset,
                   reportTo: ActorRef[Coordinator.Response]
                 ) {

  import Coordinator.*

  def start(): Behavior[Command] = {
    val loader = ctx.spawn(DatasetLoader(ctx.self, tsManager), s"loader-$id")
    ctx.watch(loader)
    loader ! DatasetLoader.LoadDataset(dataset.id, dataset.path, ctx.messageAdapter(LoadDatasetResponse.apply))

    initializing(Seq.empty)
  }

  private def initializing(tsIds: Seq[Long]): Behavior[Command] = Behaviors.receiveMessagePartial{
    case GetStatus(replyTo) =>
      replyTo ! ProcessingStatus(id, Initializing)
      Behaviors.same
    case NewTimeSeries(datasetId, tsId) =>
      ctx.log.info("New time series {} for dataset {} was loaded!", tsId, datasetId)
//      if !tsIds.empty then
        // send out approximation messages
      initializing(tsIds :+ tsId)

    case LoadDatasetResponse(DatasetLoader.DatasetLoaded(_, tsIds)) =>
      // switch to approximating state
      Behaviors.same
    case LoadDatasetResponse(DatasetLoader.DatasetNotLoaded(_, reason)) =>
      reportTo ! ProcessingFailed(id)
      Behaviors.stopped
  }
}
