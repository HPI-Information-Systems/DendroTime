package de.hpi.fgis.dendrotime

import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.{Hierarchy, computeHierarchy}
import de.hpi.fgis.dendrotime.io.TsParser
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVWriter
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

import java.io.File

class SerialHAC(settings: Settings) {
  import settings.Distances.given

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
    println(s"  ... loaded $n TS in ${System.currentTimeMillis() - startTime} ms")

    println(s"  computing $m pairwise distances ...")
    val t0 = System.currentTimeMillis()
    val dists = PDist(params.metric.pairwise(timeseries.map(_.data)), n)
    println(s"  ... done in ${System.currentTimeMillis() - t0} ms")

    println("  running SerialHAC ...")
    val t1 = System.currentTimeMillis()
    val hierarchy = computeHierarchy(dists, params.linkage)
    println(s"  ... done in ${System.currentTimeMillis() - t1} ms")

    println(s"Finished SeriesHAC in ${System.currentTimeMillis() - startTime} ms")

    if settings.storeResults then
      val hierarchyFile = settings.resultsPath.resolve(s"${dataset.name}-serial/hierarchy.csv").toFile
      HierarchyCSVWriter.write(hierarchyFile, hierarchy)
    hierarchy
  }
}
