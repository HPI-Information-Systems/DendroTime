package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.scaladsl.{ActorContext, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.clusterer.ClustererProtocol
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import org.slf4j.Logger

case class WorkerContext(
                          context: ActorContext[WorkerProtocol.Command],
                          stashBuffer: StashBuffer[WorkerProtocol.Command],
                          tsManager: ActorRef[TsmProtocol.Command],
                          clusterer: ActorRef[ClustererProtocol.Command],
                        ) {

  val settings: Settings = Settings(context.system)

  def stash(m: WorkerProtocol.Command): Unit = stashBuffer.stash(m)

  def unstashAll(behavior: Behavior[WorkerProtocol.Command]): Behavior[WorkerProtocol.Command] =
    stashBuffer.unstashAll(behavior)

  def log: Logger = context.log

  def self: ActorRef[WorkerProtocol.Command] = context.self
}
