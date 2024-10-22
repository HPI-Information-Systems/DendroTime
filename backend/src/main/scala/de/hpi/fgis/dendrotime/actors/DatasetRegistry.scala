package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset

import java.nio.file.{Files, Path, Paths}
import scala.util.{Failure, Success, Try}

object DatasetRegistry {

  sealed trait Command
  case class GetDatasets(replyTo: ActorRef[GetDatasetsResponse]) extends Command
  case class GetDataset(id: Int, replyTo: ActorRef[GetDatasetResponse]) extends Command
  case class AddDataset(dataset: Dataset, replyTo: ActorRef[AddDatasetResponse]) extends Command
  case class RemoveDataset(id: Int, replyTo: ActorRef[RemoveDatasetResponse]) extends Command

  final case class GetDatasetsResponse(datasets: Seq[Dataset])
  final case class GetDatasetResponse(dataset: Option[Dataset])
  sealed trait AddDatasetResponse
  final case class DatasetAdded(dataset: Dataset) extends AddDatasetResponse
  final case class DatasetNotAdded(reason: String) extends AddDatasetResponse
  final case class RemoveDatasetResponse(id: Int)

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    new DatasetRegistry(ctx).start()
  }
}

private class DatasetRegistry private (ctx: ActorContext[DatasetRegistry.Command]) {
  import DatasetRegistry.*

  private val dataPath = Settings(ctx.system).dataPath

  private def start(): Behavior[Command] = {
    val datasets = loadExistingDatasets
    running(datasets)
  }

  private def running(datasets: Map[Int, Dataset]): Behavior[Command] = Behaviors.receiveMessage{
    case GetDatasets(replyTo) =>
      replyTo ! GetDatasetsResponse(datasets.values.toSeq.sorted)
      Behaviors.same

    case GetDataset(id, replyTo) =>
      replyTo ! GetDatasetResponse(datasets.get(id))
      Behaviors.same

    case AddDataset(dataset, replyTo) =>
      if datasets.contains(dataset.id) then
        replyTo ! DatasetNotAdded("Dataset with id d-${dataset.id} already exists")
        Behaviors.same
      else
        val nextId = datasets.keys.max + 1
        cacheNewDataset(dataset, nextId) match
          case Success(newDataset) =>
            replyTo ! DatasetAdded(newDataset)
            running(datasets + (newDataset.id -> newDataset))
          case Failure(e) =>
            replyTo ! DatasetNotAdded(e.toString)
            Behaviors.same

    case RemoveDataset(id, replyTo) =>
      replyTo ! RemoveDatasetResponse(id)
      running(datasets - id)
  }

  private def loadExistingDatasets: Map[Int, Dataset] = {
    val localDatasetsFolder = dataPath.toFile
    if !localDatasetsFolder.exists() then
      localDatasetsFolder.mkdirs()

    ctx.log.info("Loading existing datasets from {}", localDatasetsFolder)
    val dataFiles = localDatasetsFolder.listFiles(_.isDirectory).flatMap { file =>
      val files = file.listFiles()
          .filter(_.getName.endsWith(".ts"))
          .filter(_.isFile)
      files.groupBy(_.getName.stripSuffix(".ts").stripSuffix("_TEST").stripSuffix("_TRAIN")).flatMap {
        case (name, Array(f1, f2)) =>
          if f1.getName.contains("TEST") then
            Some((name, f1.getCanonicalPath, Some(f2.getCanonicalPath)))
          else if f2.getName.contains("TEST") then
            Some((name, f2.getCanonicalPath, Some(f1.getCanonicalPath)))
          else
            None
        case (name, Array(testFile)) =>
          Some((name, testFile.getCanonicalPath, None))
        case _ => None
      }
    }

    dataFiles.sorted.zipWithIndex.map { case ((name, test, train), i) =>
      (i, Dataset(i, name, test, train))
    }.toMap
  }

  private def cacheNewDataset(dataset: Dataset, newId: Int): Try[Dataset] = Try {
    val testSource = Path.of(dataset.testPath).toRealPath()
    val testTarget = copyFile(testSource, newId).toString
    val trainTarget = dataset.trainPath.map { trainPath =>
      val trainSource = Path.of(trainPath).toRealPath()
      copyFile(trainSource, newId).toString
    }
    dataset.copy(id = newId, testPath = testTarget, trainPath = trainTarget)
  }
  
  private def copyFile(source: Path, newId: Int): Path = {
    val target = dataPath.resolve(newId.toString).resolve(source.getFileName.toString)
    target.getParent.toFile.mkdirs()
    Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
  }
}
