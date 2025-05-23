package de.hpi.fgis.dendrotime.runner

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import caseapp.{CaseApp, RemainingArgs}
import com.typesafe.config.{Config, ConfigFactory}
import de.hpi.fgis.dendrotime.Settings
import de.hpi.fgis.dendrotime.io.CSVWriter
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.structures.Status

import java.nio.file.Path

object App extends CaseApp[Arguments] {

  def run(options: Arguments, remainingArgs: RemainingArgs): Unit = {
    println(s"${options.toString}, $remainingArgs")

    if options.serial && options.parallel then
      throw new IllegalArgumentException(
        "Option 'serial' and 'parallel' cannot be used simultaneously!"
      )

    // load configuration
    val config = loadConfig()
    given Config = config
    val settings = Settings.fromConfig(config)
    println(settings)

    // parse options and create parameters for DendroTime
    val dataset = loadDataset(settings.dataPath, options.dataset)
    val params = DendroTimeParams(
      distanceName = options.distance,
      linkageName = options.linkage,
      strategy = options.strategy
    )
    println(s"Dataset: $dataset")
    println(s"Parameters: $params")

    if options.serial || options.parallel then
      runSerial(settings, dataset, params, options.parallel)
    else
      runProgressive(settings, dataset, params)
  }

  private def runSerial(settings: Settings, dataset: Dataset, params: DendroTimeParams, parallel: Boolean): Unit = {
    val serialHAC = new SerialHAC(settings, parallel)
    serialHAC.run(dataset, params)
  }

  private def runProgressive(settings: Settings, dataset: Dataset, params: DendroTimeParams)(using config: Config): Unit = {
    // create actor system
    val system = ActorSystem(Runner.create(), "dendro-time-runner", config)
    // CoordinatedShutdown(system).addActorTerminationTask(CoordinatedShutdown.PhaseServiceUnbind, "stop-http-server", system, Server.Stop)
    CoordinatedShutdown(system).addJvmShutdownHook({
      println(s"Received termination signal: stopping runner!")
      system ! Runner.Stop
    })

    // start runner by sending a message
    system ! Runner.ProcessDataset(dataset, params)

    // system automatically stops when dataset is processed
  }

  private def loadConfig(): Config = {
//    val overwrites =
//      s"""dendrotime {
//         |  store-results = true
//         |  reporting-interval = 30m
//         |  progress-indicators.ground-truth-loading-delay = 100ms
//         |}
//         |akka.loglevel = WARNING
//         |""".stripMargin
//    ConfigFactory.parseString(overwrites)
//      .withFallback(ConfigFactory.load())
    ConfigFactory.load()
  }

  private def loadDataset(dataPath: Path, datasetName: String): Dataset = {
    val localDatasetsFolder = dataPath.toAbsolutePath.toFile
    if !localDatasetsFolder.exists() then
      throw new IllegalArgumentException(s"Data path $localDatasetsFolder does not exist")

    val datasetOption = localDatasetsFolder
      .listFiles(_.isDirectory)
      .flatMap { folder =>
        folder.listFiles()
          .filter(_.getName.endsWith(".ts"))
          .filter(_.isFile)
          .groupBy(_.getName.stripSuffix(".ts").stripSuffix("_TEST").stripSuffix("_TRAIN"))
      }
      .find((name, _) => name == datasetName)
      .flatMap {
        case (name, Array(f1, f2)) =>
          if f1.getName.contains("TEST") then
            Some(Dataset(0, name, f1.getCanonicalPath, Some(f2.getCanonicalPath)))
          else if f2.getName.contains("TEST") then
            Some(Dataset(0, name, f2.getCanonicalPath, Some(f1.getCanonicalPath)))
          else
            None
        case (name, Array(testFile)) =>
          Some(Dataset(0, name, testFile.getCanonicalPath, None))
        case _ => None
      }

    if datasetOption.isEmpty then
      throw new IllegalArgumentException(s"Dataset $datasetName not found in $localDatasetsFolder")
    datasetOption.get
  }

  def storeRuntimes(runtimes: scala.collection.Map[Status, Long], path: Path): Unit = {
    val runtimesFile = path.resolve("runtimes.csv").toFile
    val data = runtimes.iterator.toArray
      .sortBy(_._1)
      .map((status, runtime) => Array(status.toString, runtime.toString))
    CSVWriter.write(runtimesFile, data, Array("phase", "runtime"))
  }
}
