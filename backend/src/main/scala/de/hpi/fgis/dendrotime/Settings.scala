package de.hpi.fgis.dendrotime

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigException}
import de.hpi.fgis.bloomfilter.BloomFilterOptions
import de.hpi.fgis.dendrotime.actors.clusterer.ClusterSimilarityOptions
import de.hpi.fgis.dendrotime.clustering.distances.DistanceOptions
import de.hpi.fgis.dendrotime.clustering.distances.DistanceOptions.{DTWOptions, MSMOptions, SBDOptions}

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration

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

  val storeResults: Boolean = config.getBoolean(s"$namespace.store-results")

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

  val batchingMaxBatchSize: Int = config.getInt(s"$namespace.batching.max-batch-size")

  object ProgressIndicators {
    private val internalNamespace = s"$namespace.progress-indicators"
    val computeHierarchySimilarity: Boolean = config.getBoolean(s"$internalNamespace.hierarchy-similarity")
    val computeHierarchyQuality: Boolean = config.getBoolean(s"$internalNamespace.hierarchy-quality")
    val computeClusterQuality: Boolean = config.getBoolean(s"$internalNamespace.cluster-quality")
    val loadingDelay: FiniteDuration = {
      val duration = config.getDuration(s"$internalNamespace.ground-truth-loading-delay")
      FiniteDuration(duration.toMillis, "milliseconds")
    }
    val disabled: Boolean = !computeHierarchySimilarity && !computeHierarchyQuality && !computeClusterQuality
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

      given sbdOpts: SBDOptions = SBDOptions(config.getBoolean(s"$internalNamespace.standardize"))
    }

    object DTW {
      private val internalNamespace = s"$namespace.distances.dtw"

      given dtwOpts: DTWOptions = DTWOptions(
        window = config.getDouble(s"$internalNamespace.window"),
        itakuraMaxSlope = config.getDouble(s"$internalNamespace.itakura-max-slope")
      )
    }

    given options: DistanceOptions = DistanceOptions(Distances.MSM.msmOpts, Distances.DTW.dtwOpts, Distances.SBD.sbdOpts)
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

  given clusterSimilarityOptions: ClusterSimilarityOptions = {
    val similarity = ClusterSimilarityOptions.Similarity(
      config.getString(s"$namespace.cluster-similarity.similarity")
    )
    val aggregation = ClusterSimilarityOptions.Aggregation(
      config.getString(s"$namespace.cluster-similarity.aggregation"),
      if (config.hasPath(s"$namespace.cluster-similarity.decaying-factor"))
        Some(config.getDouble(s"$namespace.cluster-similarity.decaying-factor"))
      else
        None
    )
    val cardLowerBound = config.getInt(s"$namespace.cluster-similarity.cardinality-lower-bound")
    val cardUpperBound = config.getInt(s"$namespace.cluster-similarity.cardinality-upper-bound")
    ClusterSimilarityOptions(bloomFilterOptions, similarity, aggregation, cardLowerBound, cardUpperBound)
  }

  override def toString: String =
    s"""Settings(
       |  host=$host,
       |  port=$port,
       |  dataPath=$dataPath,
       |  resultsPath=$resultsPath,
       |  storeResults=$storeResults,
       |  askTimeout=$askTimeout,
       |  numberOfWorkers=$numberOfWorkers,
       |  maxTimeseries=$maxTimeseries,
       |  reportingInterval=$reportingInterval,
       |  ProgressIndicators(
       |    computeHierarchySimilarity=${ProgressIndicators.computeHierarchySimilarity},
       |    computeHierarchyQuality=${ProgressIndicators.computeHierarchyQuality},
       |    computeClusterQuality=${ProgressIndicators.computeClusterQuality},
       |    loadingDelay=${ProgressIndicators.loadingDelay}
       |  ),
       |  bloomFilterOptions=$bloomFilterOptions,
       |  clusterSimilarityOptions=$clusterSimilarityOptions,
       |)""".stripMargin
}
