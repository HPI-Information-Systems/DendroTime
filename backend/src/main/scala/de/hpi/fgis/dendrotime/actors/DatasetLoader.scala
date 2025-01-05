package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, Props}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.io.TsParser
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

import java.io.File
import scala.collection.immutable.NumericRange
import scala.util.{Failure, Success, Try}


object DatasetLoader {
  sealed trait Command
  case class LoadDataset(d: Dataset, replyTo: ActorRef[Response]) extends Command

  sealed trait Response
  case class DatasetLoaded(id: Int, tsIds: NumericRange[Long]) extends Response
  case class DatasetNotLoaded(id: Int, reason: String) extends Response
  case class DatasetNTimeseries(n: Int) extends Response
  case class NewTimeSeries(datasetId: Int, tsId: Long) extends Response

  def apply(tsManager: ActorRef[TsmProtocol.Command]): Behavior[Command] = Behaviors.setup { ctx =>
    new DatasetLoader(ctx, tsManager).start()
  }

  def props: Props = DispatcherSelector.blocking()
}

private class DatasetLoader private (
                             ctx: ActorContext[DatasetLoader.Command],
                             tsManager: ActorRef[TsmProtocol.Command]
                           ) {

  import DatasetLoader.*
  
  private var idGen = 0L
  private val settings = Settings(ctx.system)
  private val parser = TsParser(TsParser.TsParserSettings(
    parseMetadata = false,
    tsLimit = settings.maxTimeseries
  ))

  private def start(): Behavior[Command] = Behaviors.receiveMessagePartial {
    case LoadDataset(d, replyTo) =>
      val lastId = idGen
      ctx.log.info("Loading dataset d-{} from {} and {}", d.id, d.testPath, d.trainPath)
      loadDataset(d, replyTo) match {
        case _: Success[Unit] =>
          val count = idGen - lastId
          ctx.log.trace("Dataset d-{} loaded with {} instances", d.id, count)
          replyTo ! DatasetLoaded(d.id, lastId until idGen)
        case Failure(e) =>
          ctx.log.error(s"Failed to load dataset d-${d.id}", e)
          replyTo ! DatasetNotLoaded(d.id, e.getMessage)
      }
      Behaviors.same
  }

  private def loadDataset(d: Dataset, replyTo: ActorRef[Response]): Try[Unit] = Try {
    val testFile = new File(d.testPath)
    val nTestTimeseries = parser.countTimeseries(testFile)
    val nTrainTimeseries = d.trainPath match {
      case Some(path) =>
        parser.countTimeseries(new File(path))
      case None =>
        0
    }
    replyTo ! DatasetNTimeseries(nTestTimeseries + nTrainTimeseries)

    var idx = 0
    val processor = new TsParser.TsProcessor {
      override def processUnivariate(data: Array[Double], label: String): Unit = {
        val ts = LabeledTimeSeries(idGen, idx, data, label)
        tsManager ! TsmProtocol.AddTimeSeries(d.id, ts)
        replyTo ! NewTimeSeries(datasetId = d.id, tsId = idGen)
        idGen += 1
        idx += 1
      }
    }
    d.trainPath.foreach { path =>
      parser.parse(new File(path), processor)
    }
    parser.parse(testFile, processor)
  }
}
