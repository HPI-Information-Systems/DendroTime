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
  case class NewTimeSeries(datasetId: Int, tsId: Long) extends Response

  def apply(tsManager: ActorRef[TimeSeriesManager.Command]): Behavior[Command] = Behaviors.setup { ctx =>
    new DatasetLoader(ctx, tsManager).start()
  }
}

private class DatasetLoader private (
                             ctx: ActorContext[DatasetLoader.Command],
                             tsManager: ActorRef[TimeSeriesManager.Command]
                           ) {

  import DatasetLoader.*
  
  private var idGen = 0L
  private val parser = TsParser(TsParser.TsParserSettings(parseMetadata = false))

  private def start(): Behavior[Command] = Behaviors.receiveMessagePartial {
    case LoadDataset(id, path, replyTo) =>
      val lastId = idGen
      ctx.log.info("Loading dataset d-{} from {}", id, path)
      loadDataset(id, path, replyTo) match {
        case _: Success[Unit] =>
          val count = idGen - lastId
          ctx.log.info("Dataset d-{} loaded with {} instances", id, count)
          replyTo ! DatasetLoaded(id, lastId until idGen)
        case Failure(e) =>
          ctx.log.error(s"Failed to load dataset d-$id", e)
          replyTo ! DatasetNotLoaded(id, e.getMessage)
      }
      Behaviors.same
  }

  private def loadDataset(id: Int, path: String, replyTo: ActorRef[Response]): Try[Unit] = Try {
    val file = new File(path)
    parser.parse(file, new TsParser.TsProcessor {
      override def processUnivariate(data: Array[Double], label: String): Unit = {
        ctx.log.trace("New univariate TS ts-{} with d-{} values and label '{}'", idGen, data.length, label)
        val ts = LabeledTimeSeries(idGen, data, label)
        tsManager ! TimeSeriesManager.AddTimeSeries(id, ts)
        replyTo ! NewTimeSeries(datasetId = id, tsId = idGen)
        idGen += 1
      }
    })
  }
}
