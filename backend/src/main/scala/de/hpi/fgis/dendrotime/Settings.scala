package de.hpi.fgis.dendrotime

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import akka.util.Timeout
import com.typesafe.config.Config
import de.hpi.fgis.dendrotime.clustering.distances.Distance
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage

import scala.concurrent.duration.FiniteDuration

object Settings extends ExtensionId[Settings] {

  override def createExtension(system: ActorSystem[_]): Settings = new Settings(system.settings.config)

  def fromConfig(config: Config): Settings = new Settings(config)
}

class Settings private (config: Config) extends Extension {
  private val namespace = "dendrotime"

  val host: String = config.getString(s"$namespace.host")
  val port: Int = config.getInt(s"$namespace.port")

  val dataPath: String = config.getString(s"$namespace.data-path")
  
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
  
  val linkage: Linkage = Linkage(config.getString(s"$namespace.linkage"))
  val distance: Distance = Distance(config.getString(s"$namespace.distance"))
}
