package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import de.hpi.fgis.dendrotime.actors.TimeSeriesManager
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer

case class WorkerContext(
  context: ActorContext[Worker.Command],
  tsManager: ActorRef[TimeSeriesManager.Command],
  clusterer: ActorRef[Clusterer.Command],
)
