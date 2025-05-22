package de.hpi.fgis.dendrotime.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.io.CSVWriter
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVWriter
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.{ClusteringState, ProgressMessage}
import de.hpi.fgis.dendrotime.structures.{QualityTrace, Status}
import org.apache.commons.math3.util.FastMath

import scala.collection.mutable
import scala.language.postfixOps
import scala.math.Ordering.Implicits.infixOrderingOps

object Communicator {

  sealed trait Command
  final case class NewStatus(status: Status) extends Command
  final case class ProgressUpdate(status: Status, progress: Int) extends Command
  final case class NewHierarchy(state: ClusteringState) extends Command
  final case class GetProgress(replyTo: ActorRef[ProgressMessage]) extends Command
  private case object ReportStatus extends Command

  def apply(dataset: Dataset, params: DendroTimeParams): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      timers.startTimerAtFixedRate(ReportStatus, Settings(ctx.system).reportingInterval)
      new Communicator(ctx, dataset, params).running(Status.Initializing, ClusteringState())
    }
  }

  extension (b: Boolean)
    def toInt: Int = if b then 1 else 0
}

private class Communicator private(ctx: ActorContext[Communicator.Command],
                                   dataset: Dataset,
                                   params: DendroTimeParams) {

  import Communicator.*

  private val settings = Settings(ctx.system)
  private val progress = mutable.SortedMap[Status, Int](
    Status.Initializing -> 20,
    Status.Approximating -> 0,
    Status.ComputingFullDistances -> 0,
    Status.Finalizing -> 0,
    Status.Finished -> 100
  )

  private def running(status: Status, clusteringState: ClusteringState): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
      case NewStatus(newStatus) =>
        // set all previous progresses to 100
        for k <- progress.keys if k < newStatus do
          progress(k) = 100
        ctx.log.debug("Updating progress bc. state changed to {}: {}", newStatus, progress)
        running(newStatus, clusteringState)

      case ProgressUpdate(s, p) =>
        ctx.log.debug("Received progress update for {}: {}", s, p)
        progress.update(s, p)
        running(status, clusteringState)

      case NewHierarchy(state) =>
        if state.qualityTrace.nonEmpty then
          ctx.log.debug("Received new hierarchy with last index {}!", state.qualityTrace.indices.last)
        if settings.ProgressIndicators.toStdout then
          logQualityToStdout(state.qualityTrace)
        running(status, state)

      case GetProgress(replyTo) =>
        replyTo ! ProgressMessage.progressFromClusteringState(status, progress(status), clusteringState)
        Behaviors.same

      case ReportStatus =>
        ctx.log.info("[REPORT] Current status: {}, progress: {}", status, progress)
        Behaviors.same

    } receiveSignal {
      case (_, PostStop) =>
        ctx.log.info("[REPORT] Final status: {}, progress: {}", status, progress)
        // FIXME: remove safety check
        if clusteringState.qualityTrace.hasGtSimilarities then
          if clusteringState.qualityTrace.gtSimilarities.last != 1.0 then
            ctx.log.error("Final hierarchy similarity is not 1.0! This might indicate a problem with the clustering.")
          else
            ctx.log.info("Final hierarchy similarity is 1.0. Clustering seems to be fine.")
        if settings.storeResults then
          ctx.log.info("Storing final results to results folder!")
          saveFinalState(status, progress(status), clusteringState)
        Behaviors.same
    }

  private def saveFinalState(status: Status, progress: Int, clusteringState: ClusteringState): Unit = {
    val destination = settings.resolveResultsFolder(dataset, params).resolve(s"$status-$progress")
    destination.toFile.mkdirs()

    // write settings
    val settingsFile = destination.resolve("config.json").toFile
    settings.writeJson(settingsFile)

    // write hierarchy
    val hierarchyFile = destination.resolve("hierarchy.csv").toFile
    HierarchyCSVWriter.write(hierarchyFile, clusteringState.hierarchy)

    // write qualities
    val trace = clusteringState.qualityTrace
    val qualityFile = destination.resolve("qualities.csv").toFile
    val dims = 3 + trace.hasClusterQualities.toInt + trace.hasGtSimilarities.toInt
    val header = mkHeader(trace)
    val matrix = Array.ofDim[Double](trace.size, dims)

    for i <- 0 until trace.size do
      val row = Array.ofDim[Double](dims)
      row(0) = trace.indices(i).toDouble
      row(1) = trace.timestamps(i).toDouble
      row(2) = trace.similarities(i)
      var offset = 3
      if trace.hasGtSimilarities then
        row(offset) = trace.gtSimilarities(i)
        offset += 1
      if trace.hasClusterQualities then
        row(offset) = trace.clusterQualities(i)
      matrix(i) = row

    CSVWriter.write(qualityFile, matrix, header)
  }

  private def mkHeader(trace: QualityTrace): Array[String] = {
    val dims = 3 + trace.hasClusterQualities.toInt + trace.hasGtSimilarities.toInt
    val header = Array.ofDim[String](dims)
    var i = 0

    def add(name: String): Unit =
      header(i) = name
      i += 1

    add("index")
    add("timestamp")
    add("hierarchy-similarity")
    if trace.hasGtSimilarities then add("hierarchy-quality")
    if trace.hasClusterQualities then add("cluster-quality")
    header
  }

  private def logQualityToStdout(trace: QualityTrace): Unit = {
    if trace.nonEmpty then
      val index = trace.indices.last
      val similarities = trace.similarities
      val timestamps = trace.timestamps
      var nPoints = FastMath.min(100, similarities.length*0.1).toInt
      if similarities.length < 100 then
        nPoints = similarities.length
      val progInd = 1000 * (similarities.last - similarities(similarities.length - nPoints)) / (timestamps.last - timestamps(timestamps.length - nPoints))

      if trace.hasGtSimilarities then
        val hierarchyQuality = trace.gtSimilarities.last
        println(f"Convergence @ $index%6d: $progInd%.4f ($hierarchyQuality%.2f)")
      else
        println(f"Convergence @ $index%6d: $progInd%.4f")
  }
}
