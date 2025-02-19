package de.hpi.fgis.dendrotime.actors.coordinator.strategies

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, StashBuffer}
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.StrategyEvent
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams

import scala.collection.IndexedSeq

object StrategyParameters {
  def apply(dataset: Dataset,
            params: DendroTimeParams,
            tsManager: ActorRef[TsmProtocol.Command],
            clusterer: ActorRef[ClustererProtocol.Command]): StrategyParameters =
    ExternalStrategyParameters(dataset, params, tsManager, clusterer)

  private case class ExternalStrategyParameters(
                                                 dataset: Dataset,
                                                 params: DendroTimeParams,
                                                 tsManager: ActorRef[TsmProtocol.Command],
                                                 clusterer: ActorRef[ClustererProtocol.Command]
                                               ) extends StrategyParameters {
    private[strategies] override def toInternal(
                                                 eventReceiver: ActorRef[StrategyEvent],
                                                 ctx: ActorContext[StrategyProtocol.StrategyCommand],
                                                 stash: StashBuffer[StrategyProtocol.StrategyCommand],
                                                 timeseriesIds: IndexedSeq[Int]
                                               ): InternalStrategyParameters =
      InternalStrategyParameters(dataset, params, tsManager, clusterer, eventReceiver, ctx, stash, timeseriesIds)
  }

  private[strategies] case class InternalStrategyParameters(
                                                             dataset: Dataset,
                                                             params: DendroTimeParams,
                                                             tsManager: ActorRef[TsmProtocol.Command],
                                                             clusterer: ActorRef[ClustererProtocol.Command],
                                                             eventReceiver: ActorRef[StrategyEvent],
                                                             ctx: ActorContext[StrategyProtocol.StrategyCommand],
                                                             stash: StashBuffer[StrategyProtocol.StrategyCommand],
                                                             timeseriesIds: IndexedSeq[Int]
                                                           ) extends StrategyParameters {

    private[strategies] override def toInternal(
                                                 eventReceiver: ActorRef[StrategyEvent],
                                                 ctx: ActorContext[StrategyProtocol.StrategyCommand],
                                                 stash: StashBuffer[StrategyProtocol.StrategyCommand],
                                                 timeseriesIds: IndexedSeq[Int]
                                               ): InternalStrategyParameters = this
  }
}

trait StrategyParameters {
  val dataset: Dataset
  val params: DendroTimeParams
  val tsManager: ActorRef[TsmProtocol.Command]
  val clusterer: ActorRef[ClustererProtocol.Command]

  private[strategies] def toInternal(
                                      eventReceiver: ActorRef[StrategyEvent],
                                      ctx: ActorContext[StrategyProtocol.StrategyCommand],
                                      stash: StashBuffer[StrategyProtocol.StrategyCommand],
                                      timeseriesIds: IndexedSeq[Int]
                                    ): StrategyParameters.InternalStrategyParameters
}