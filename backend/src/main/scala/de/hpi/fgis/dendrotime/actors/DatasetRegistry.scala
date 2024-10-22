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
    localDatasetsFolder.listFiles(_.isDirectory).sorted.flatMap { file =>
      file.listFiles()
          .filter(_.getName.endsWith(".ts"))
          .filter(_.isFile)
    }.zipWithIndex.map((file, i) =>
      (i, Dataset(i, file.getName, file.getCanonicalPath))
    ).toMap
  }

  private def cacheNewDataset(dataset: Dataset, newId: Int): Try[Dataset] = Try {
    val file = Path.of(dataset.path).toRealPath()
    val target = dataPath.resolve(newId.toString).resolve(file.getFileName.toString)
    target.getParent.toFile.mkdir()
    Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    dataset.copy(id = newId, path = target.toRealPath().toString)
  }
}
