package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import de.hpi.fgis.dendrotime.actors.clusterer.Clusterer
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol

case class WorkerContext(
                          context: ActorContext[WorkerProtocol.Command],
                          tsManager: ActorRef[TsmProtocol.Command],
                          clusterer: ActorRef[Clusterer.Command],
                        )
