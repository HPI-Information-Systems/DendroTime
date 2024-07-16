package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object Communicator {
  
  sealed trait Command
  
  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    new Communicator(ctx).start()
  }
}

private class Communicator private (ctx: ActorContext[Communicator.Command]) {
  import Communicator.*
  
  private def start(): Behavior[Command] = Behaviors.receiveMessage(_ =>
      Behaviors.same
  )
}
