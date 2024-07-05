package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.io.TsParser
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

import java.io.File
import scala.collection.immutable.NumericRange
import scala.util.{Failure, Success, Try}


object DatasetLoader {
  sealed trait Command
  case class LoadDataset(id: Int, path: String, replyTo: ActorRef[Response]) extends Command

  sealed trait Response
  case class DatasetLoaded(id: Int, tsIds: NumericRange[Long]) extends Response
  case class DatasetNotLoaded(id: Int, reason: String) extends Response

  def apply(coordinator: ActorRef[Coordinator.Command], tsManager: ActorRef[TimeSeriesManager.Command]): Behavior[Command] = Behaviors.setup { ctx =>
    new DatasetLoader(ctx, coordinator, tsManager).start()
  }
}

private class DatasetLoader(
                             ctx: ActorContext[DatasetLoader.Command],
                             coordinator: ActorRef[Coordinator.Command],
                             tsManager: ActorRef[TimeSeriesManager.Command]
                           ) {

  import DatasetLoader.*
  
  private var idGen = 0L
  private val parser = TsParser(TsParser.TsParserSettings(parseMetadata = false))

  def start(): Behavior[Command] = Behaviors.receiveMessagePartial {
    case LoadDataset(id, path, replyTo) =>
      val lastId = idGen
      loadDataset(id, path) match {
        case _: Success[Unit] =>
          val count = idGen - lastId
          ctx.log.info("Dataset {} loaded with {} instances", id, count)
          replyTo ! DatasetLoaded(id, lastId until idGen)
        case Failure(e) =>
          ctx.log.error(s"Failed to load dataset $id", e)
          replyTo ! DatasetNotLoaded(id, e.getMessage)
      }
      Behaviors.same
  }

  private def loadDataset(id: Int, path: String): Try[Unit] = Try {
    val file = new File(path)
    parser.parse(file, new TsParser.TsProcessor {
      override def processUnivariate(data: Array[Double], label: String): Unit = {
        ctx.log.info("New univariate TS {} with {} values and label '{}'", idGen, data.length, label)
        val ts = LabeledTimeSeries(idGen, data, label)
        tsManager ! TimeSeriesManager.AddTimeSeries(id, ts)
        coordinator ! Coordinator.NewTimeSeries(datasetId = id, tsId = idGen)
        idGen += 1
      }
    })
  }
}
