package de.hpi.fgis.dendrotime.runner

import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.distances.Distance
import de.hpi.fgis.dendrotime.clustering.hierarchy.{Hierarchy, computeHierarchy}
import de.hpi.fgis.dendrotime.io.TimeSeries.LabeledTimeSeries
import de.hpi.fgis.dendrotime.io.TsParser
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVWriter
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.structures.Status

import java.io.File
import java.util.concurrent.Executors
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class SerialHAC(settings: Settings, parallel: Boolean) {

  import settings.Distances.given

  private val runtimes = mutable.Map.empty[Status, Long]
  private val name = if parallel then "ParallelHAC" else "SerialHAC"

  def run(dataset: Dataset, params: DendroTimeParams): Hierarchy = {
    println(s"Running $name on dataset $dataset with parameters $params")
    val startTime = System.currentTimeMillis()

    println(s"  loading dataset ${dataset.id} ...")
    val trainTimeseries = dataset.trainPath.fold(Array.empty[LabeledTimeSeries]) { path =>
      val f = new File(path).getAbsoluteFile
      TsParser.loadAllLabeledTimeSeries(f)
    }
    val testPath = new File(dataset.testPath).getAbsoluteFile
    val timeseries = trainTimeseries ++ TsParser.loadAllLabeledTimeSeries(testPath, idOffset = trainTimeseries.length)
    val n = timeseries.length
    val m = n * (n - 1) / 2
    val dataDuration = System.currentTimeMillis() - startTime
    runtimes(Status.Initializing) = dataDuration
    println(s"  ... loaded $n TS in $dataDuration ms")

    println(s"  computing $m pairwise distances ...")
    val t0 = System.currentTimeMillis()
    val dists =
      if parallel then computeDistancesParallel(params, timeseries, settings.numberOfWorkers)
      else computeDistancesSerial(params, timeseries)
    val distanceDuration = System.currentTimeMillis() - t0
    runtimes(Status.Approximating) = 0L
    runtimes(Status.ComputingFullDistances) = distanceDuration
    println(s"  ... done in $distanceDuration ms")

    println("  running HAC ...")
    val t1 = System.currentTimeMillis()
    val hierarchy = computeHierarchy(dists, params.linkage)
    val hacDuration = System.currentTimeMillis() - t1
    runtimes(Status.Finalizing) = hacDuration
    println(s"  ... done in $hacDuration ms")

    val duration = System.currentTimeMillis() - startTime
    runtimes(Status.Finished) = duration
    println(s"Finished $name in $duration ms")

    if settings.storeResults then
      val datasetPath = settings.resolveResultsFolder(dataset, params)
      val resultFolder =
        if parallel then datasetPath.resolve("parallel")
        else datasetPath.resolve("serial")
      resultFolder.toFile.mkdirs()
      val hierarchyFile = resultFolder.resolve("hierarchy.csv").toFile
      val settingsFile = resultFolder.resolve("config.json").toFile
      HierarchyCSVWriter.write(hierarchyFile, hierarchy)
      App.storeRuntimes(runtimes, resultFolder)
      settings.writeJson(settingsFile)
    hierarchy
  }

  private def computeDistancesSerial(params: DendroTimeParams, timeseries: Array[LabeledTimeSeries]): PDist = {
    val distance = params.distance
    PDist(distance.pairwise(timeseries.map(_.data)), timeseries.length)
  }

  private def computeDistancesParallel(params: DendroTimeParams, timeseries: Array[LabeledTimeSeries], nThreads: Int): PDist = {
    println(s"    using $nThreads threads")

    given ExecutionContext = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(nThreads))

    val localDistance = ThreadLocal.withInitial[Distance](() => params.distance)
    val n = timeseries.length
    val pdist = PDist.empty(n).mutable
    val futures = for
      i <- 0 until n
      j <- i + 1 until n
    yield
      Future {
        val distance = localDistance.get()
        pdist(i, j) = distance(timeseries(i).data, timeseries(j).data)
      }

    Await.ready(Future.sequence(futures), Duration.Inf)
    pdist
  }
}
