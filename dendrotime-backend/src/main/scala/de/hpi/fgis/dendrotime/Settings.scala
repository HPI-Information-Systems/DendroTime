package de.hpi.fgis.dendrotime

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigException, ConfigRenderOptions}
import de.hpi.fgis.bloomfilter.BloomFilterOptions
import de.hpi.fgis.dendrotime.clustering.distances.DistanceOptions
import de.hpi.fgis.dendrotime.clustering.distances.DistanceOptions.{DTWOptions, LorentzianOptions, MSMOptions, MinkowskyOptions, SBDOptions}
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.structures.HierarchySimilarityConfig

import java.io.File
import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try, Using}

object Settings extends ExtensionId[Settings] {

  override def createExtension(system: ActorSystem[_]): Settings = new Settings(system.settings.config)

  def fromConfig(config: Config): Settings = new Settings(config)
}

class Settings private(config: Config) extends Extension {
  private val namespace = "dendrotime"

  val host: String = config.getString(s"$namespace.host")
  val port: Int = config.getInt(s"$namespace.port")

  val dataPath: Path = Path.of(config.getString(s"$namespace.data-path"))
  val resultsPath: Path = Path.of(config.getString(s"$namespace.results-path"))
  val groundTruthPath: Path = Path.of(config.getString(s"$namespace.ground-truth-path"))

  val storeDistances: Boolean = config.getBoolean(s"$namespace.store-distances")
  val storeResults: Boolean = config.getBoolean(s"$namespace.store-results")

  def resolveResultsFolder(dataset: Dataset, params: DendroTimeParams): Path =
    resultsPath.resolve(
      s"${dataset.name}-${params.distanceName}-${params.linkageName}-${params.strategy.replace("-", "_")}"
    )

  val askTimeout: Timeout = {
    val duration = config.getDuration(s"$namespace.ask-timeout")
    FiniteDuration(duration.toMillis, "milliseconds")
  }

  private val maxWorkers: Int = config.getInt(s"$namespace.max-workers")

  private val cores = Runtime.getRuntime.availableProcessors()

  val numberOfWorkers: Int = Seq(maxWorkers, cores).min

  val maxTimeseries: Option[Int] =
    if config.hasPath(s"$namespace.max-timeseries") then
      Some(config.getInt(s"$namespace.max-timeseries"))
    else
      None

  val reportingInterval: FiniteDuration = {
    val duration = config.getDuration(s"$namespace.reporting-interval")
    FiniteDuration(duration.toMillis, "milliseconds")
  }

  val batchingTargetTime: FiniteDuration = {
    val duration = config.getDuration(s"$namespace.batching.target-time")
    FiniteDuration(duration.toMillis, "milliseconds")
  }

  val batchingMaxBatchSize: Option[Int] =
    if config.hasPath(s"$namespace.batching.max-batch-size") then
      Some(config.getInt(s"$namespace.batching.max-batch-size"))
    else
      None

  object ProgressIndicators {
    private val internalNamespace = s"$namespace.progress-indicators"

    private def resolveSubConfig(path: String): Option[Config] = Try[Option[Config]] {
      if !config.getBoolean(s"$internalNamespace.$path") then None
      else throw new RuntimeException("marker for recovery")
    }.orElse(Try {
      Some(config.getConfig(s"$internalNamespace.$path"))
    }).orElse(Try {
      val reference = config.getString(s"$internalNamespace.$path")
      Some(config.getConfig(s"$internalNamespace.$reference"))
    }).getOrElse(None)

    val hierarchySimilarityConfig: Option[HierarchySimilarityConfig] =
      resolveSubConfig("hierarchy-similarity").map(HierarchySimilarityConfig.fromConfig)
    val hierarchyQualityConfig: Option[HierarchySimilarityConfig] =
      resolveSubConfig("hierarchy-quality").map(HierarchySimilarityConfig.fromConfig)
    val clusterQualityMethod: Option[String] =
      if config.hasPath(s"$internalNamespace.cluster-quality") then
        Some(config.getString(s"$internalNamespace.cluster-quality"))
      else
        None
    val loadingDelay: FiniteDuration = {
      val duration = config.getDuration(s"$internalNamespace.ground-truth-loading-delay")
      FiniteDuration(duration.toMillis, "milliseconds")
    }
    val toStdout: Boolean = config.getBoolean(s"$internalNamespace.stdout")

    def computeHierarchySimilarity: Boolean = hierarchySimilarityConfig.isDefined

    def computeHierarchyQuality: Boolean = hierarchyQualityConfig.isDefined

    def computeClusterQuality: Boolean = clusterQualityMethod.isDefined

    def disabled: Boolean =
      hierarchySimilarityConfig.isEmpty && hierarchyQualityConfig.isEmpty && clusterQualityMethod.isEmpty
  }

  object Distances {
    object MSM {
      private val internalNamespace = s"$namespace.distances.msm"

      given msmOpts: MSMOptions = MSMOptions(
        cost = config.getDouble(s"$internalNamespace.cost"),
        window = config.getDouble(s"$internalNamespace.window"),
        itakuraMaxSlope = config.getDouble(s"$internalNamespace.itakura-max-slope")
      )
    }

    object SBD {
      private val internalNamespace = s"$namespace.distances.sbd"

      private val localFftwCacheSize: Option[Int] =
        if config.hasPath(s"$internalNamespace.local-fftw-cache-size") then
          Some(config.getInt(s"$internalNamespace.local-fftw-cache-size"))
        else
          None

      given sbdOpts: SBDOptions = SBDOptions(config.getBoolean(s"$internalNamespace.standardize"), localFftwCacheSize)
    }

    object DTW {
      private val internalNamespace = s"$namespace.distances.dtw"

      given dtwOpts: DTWOptions = DTWOptions(
        window = config.getDouble(s"$internalNamespace.window"),
        itakuraMaxSlope = config.getDouble(s"$internalNamespace.itakura-max-slope")
      )
    }

    object Minkowsky {
      private val internalNamespace = s"$namespace.distances.minkowsky"

      given minkowskyOpts: MinkowskyOptions = MinkowskyOptions(config.getInt(s"$internalNamespace.p"))
    }

    object Lorentzian {
      private val internalNamespace = s"$namespace.distances.lorentzian"

      given lorentzianOpts: LorentzianOptions = LorentzianOptions(config.getBoolean(s"$internalNamespace.normalize"))
    }

    val approxLength: Int = config.getInt(s"$namespace.distances.approx-length")

    given options: DistanceOptions = DistanceOptions(
      Distances.MSM.msmOpts, Distances.DTW.dtwOpts, Distances.SBD.sbdOpts, Distances.Minkowsky.minkowskyOpts,
      Distances.Lorentzian.lorentzianOpts
    )
  }

  given bloomFilterOptions: BloomFilterOptions = {
    val hashSize = config.getInt(s"$namespace.bloom-filter.murmurhash-size")
    val falsePositiveRate = config.getDouble(s"$namespace.bloom-filter.false-positive-rate")
    val bfHashSize = hashSize match {
      case 64 => BloomFilterOptions.BFHashSize.BFH64
      case 128 => BloomFilterOptions.BFHashSize.BFH128
      case _ => throw new ConfigException.BadValue(
        s"$namespace.bloom-filter.murmurhash-size", s"Unsupported murmurhash size: $hashSize"
      )
    }
    BloomFilterOptions(bfHashSize, falsePositiveRate)
  }

  def writeJson(file: File): Unit = {
    val renderOptions = ConfigRenderOptions
      .concise()
    //      .setFormatted(true)
    Using.resource(new java.io.PrintWriter(file)) { writer =>
      writer.println(config.root().render(renderOptions))
    }
  }

  override def toString: String =
    s"""Settings(
       |  host=$host,
       |  port=$port,
       |  dataPath=$dataPath,
       |  resultsPath=$resultsPath,
       |  groundTruthPath=$groundTruthPath,
       |  storeResults=$storeResults,
       |  askTimeout=$askTimeout,
       |  numberOfWorkers=$numberOfWorkers,
       |  maxTimeseries=$maxTimeseries,
       |  reportingInterval=$reportingInterval,
       |  batchingTargetTime=$batchingTargetTime,
       |  batchingMaxBatchSize=$batchingMaxBatchSize,
       |  ProgressIndicators(
       |    hierarchySimilarityConfig=${ProgressIndicators.hierarchySimilarityConfig},
       |    hierarchyQualityConfig=${ProgressIndicators.hierarchyQualityConfig},
       |    clusterQualityMethod=${ProgressIndicators.clusterQualityMethod},
       |    loadingDelay=${ProgressIndicators.loadingDelay}
       |  ),
       |  Distances(
       |    MSM=${Distances.MSM.msmOpts},
       |    SBD=${Distances.SBD.sbdOpts},
       |    DTW=${Distances.DTW.dtwOpts},
       |    Minkowsky=${Distances.Minkowsky.minkowskyOpts},
       |    Lorentzian=${Distances.Lorentzian.lorentzianOpts},
       |    approxLength=${Distances.approxLength},
       |  )
       |  bloomFilterOptions=$bloomFilterOptions,
       |)""".stripMargin
}
