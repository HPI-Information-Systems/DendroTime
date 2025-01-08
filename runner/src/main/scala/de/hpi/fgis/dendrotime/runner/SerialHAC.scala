package de.hpi.fgis.dendrotime.runner

import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.{Hierarchy, computeHierarchy}
import de.hpi.fgis.dendrotime.io.TsParser
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVWriter
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.Status
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

import java.io.File
import scala.collection.mutable

class SerialHAC(settings: Settings) {
  import settings.Distances.given

  private val runtimes = mutable.Map.empty[Status, Long]

  def run(dataset: Dataset, params: DendroTimeParams): Hierarchy = {
    println(s"Running SerialHAC on dataset $dataset with parameters $params")
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
    val dists = PDist(params.distance.pairwise(timeseries.map(_.data)), n)
    val distanceDuration = System.currentTimeMillis() - t0
    runtimes(Status.Approximating) = 0L
    runtimes(Status.ComputingFullDistances) = distanceDuration
    println(s"  ... done in $distanceDuration ms")

    println("  running SerialHAC ...")
    val t1 = System.currentTimeMillis()
    val hierarchy = computeHierarchy(dists, params.linkage)
    val hacDuration = System.currentTimeMillis() - t1
    runtimes(Status.Finalizing) = hacDuration
    println(s"  ... done in $hacDuration ms")

    val duration = System.currentTimeMillis() - startTime
    runtimes(Status.Finished) = duration
    println(s"Finished SeriesHAC in $duration ms")

    if settings.storeResults then
      val datasetPath = settings.resolveResultsFolder(dataset, params)
      val resultFolder = datasetPath.resolve("serial")
      resultFolder.toFile.mkdirs()
      val hierarchyFile = resultFolder.resolve("hierarchy.csv").toFile
      val settingsFile = resultFolder.resolve("config.json").toFile
      HierarchyCSVWriter.write(hierarchyFile, hierarchy)
      App.storeRuntimes(runtimes, resultFolder)
      settings.writeJson(settingsFile)
    hierarchy
  }
}
