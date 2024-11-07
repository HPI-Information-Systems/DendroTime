package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.io.CSVWriter
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVWriter
import de.hpi.fgis.dendrotime.model.StateModel.{ClusteringState, ProgressMessage, Status}

import scala.language.postfixOps
import scala.math.Ordering.Implicits.infixOrderingOps

object Communicator {

  sealed trait Command
  final case class NewStatus(status: Status) extends Command
  final case class ProgressUpdate(status: Status, progress: Int) extends Command
  final case class NewHierarchy(state: ClusteringState) extends Command
  final case class GetProgress(replyTo: ActorRef[ProgressMessage]) extends Command
  private case object ReportStatus extends Command

  def apply(datasetId: Int): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(ReportStatus, Settings(ctx.system).reportingInterval)
      val startProgress = Map[Status, Int](
        Status.Initializing -> 20,
        Status.Approximating -> 0,
        Status.ComputingFullDistances -> 0,
        Status.Finalizing -> 0,
        Status.Finished -> 100
      )
      new Communicator(ctx, datasetId).running(Status.Initializing, startProgress, ClusteringState())
    }
  }

  extension (b: Boolean)
    def toInt: Int = if b then 1 else 0
}

private class Communicator private(ctx: ActorContext[Communicator.Command], datasetId: Int) {

  import Communicator.*

  private val settings = Settings(ctx.system)

  private def running(status: Status, progress: Map[Status, Int], clusteringState: ClusteringState): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
      case NewStatus(newStatus) =>
        // set all previous progresses to 100
        val newP = for (k, v) <- progress yield (k, if k < newStatus then 100 else v)
        ctx.log.debug("Updating progress bc. state change to {}: {}", newStatus, newP)
        running(newStatus, newP, clusteringState)

      case ProgressUpdate(s, p) =>
        running(status, progress.updated(s, p), clusteringState)

      case NewHierarchy(state) =>
        running(status, progress, state)

      case GetProgress(replyTo) =>
        replyTo ! ProgressMessage.progressFromClusteringState(status, progress(status), clusteringState)
        Behaviors.same

      case ReportStatus =>
        ctx.log.info("[REPORT] Current status: {}, progress: {}", status, progress)
        Behaviors.same

    }.receiveSignal{
      case (_, PostStop) =>
        ctx.log.info("[REPORT] Final status: {}, progress: {}", status, progress)
        if settings.storeResults then
          ctx.log.info("Storing final results to results folder!")
          saveFinalState(status, progress(status), clusteringState)
        Behaviors.same
    }

  private def saveFinalState(status: Status, progress: Int, clusteringState: ClusteringState): Unit = {
    val destination = settings.resultsPath.resolve(s"${datasetId.toString}-${status.toString}-$progress")
    destination.toFile.mkdirs()

    // write hierarchy
    val hierarchyFile = destination.resolve("hierarchy.csv").toFile
    HierarchyCSVWriter.write(hierarchyFile, clusteringState.hierarchy)

    // write qualities
    val qualityFile = destination.resolve("quality.csv").toFile
    val dims = 3 + clusteringState.qualityTrace.hasClusterQualities.toInt + clusteringState.qualityTrace.hasGtSimilarities.toInt
    val header = Array.ofDim[String](dims)
    val matrix = Array.ofDim[Array[Double]](dims)
    header(0) = "index"
    matrix(0) = clusteringState.qualityTrace.indices.map(_.toDouble).toArray
    header(1) = "timestamp"
    matrix(1) = clusteringState.qualityTrace.timestamps.map(_.toDouble).toArray
    header(2) = "hierarchy-similarity"
    matrix(2) = clusteringState.qualityTrace.similarities.toArray
    if clusteringState.qualityTrace.hasGtSimilarities && clusteringState.qualityTrace.hasClusterQualities then
      header(3) = "hierarchy-quality"
      matrix(3) = clusteringState.qualityTrace.gtSimilarities.toArray
      header(4) = "cluster-quality"
      matrix(4) = clusteringState.qualityTrace.clusterQualities.toArray
    else if clusteringState.qualityTrace.hasClusterQualities then
      header(3) = "cluster-quality"
      matrix(3) = clusteringState.qualityTrace.clusterQualities.toArray
    else if clusteringState.qualityTrace.hasGtSimilarities then
      header(3) = "hierarchy-quality"
      matrix(3) = clusteringState.qualityTrace.gtSimilarities.toArray

    CSVWriter.write(qualityFile, matrix.transpose, header)
  }
}
