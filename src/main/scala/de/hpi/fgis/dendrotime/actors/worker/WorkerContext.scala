package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import de.hpi.fgis.dendrotime.actors.coordinator.Coordinator
import de.hpi.fgis.dendrotime.actors.{Communicator, TimeSeriesManager}

case class WorkerContext(
  context: ActorContext[Worker.Command],
  tsManager: ActorRef[TimeSeriesManager.Command],
  coordinator: ActorRef[Coordinator.Command],
  communicator: ActorRef[Communicator.Command]
)
