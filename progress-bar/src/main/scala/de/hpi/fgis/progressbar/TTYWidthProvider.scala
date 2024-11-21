package de.hpi.fgis.progressbar

import java.io.{BufferedReader, InputStreamReader}
import scala.util.Using

trait TTYWidthProvider {
  def width: Int
}

object TTYWidthProvider {
  
  object DefaultTTYWidth extends TTYWidthProvider {
    override val width: Int = 80
  }

  object TTYWidthFetcher extends TTYWidthProvider {
    // code and regex patterns from: https://github.com/jline/jline2/blob/master/src/main/java/jline/internal/TerminalLineSettings.java
    private val regex1 = "columns\\s+=\\s+(.*?)[;\\n\\r]".r
    private val regex2 = "columns\\s+([^;]*)[;\\n\\r]".r
    private val regex3 = "columns(\\S*)\\s+".r
    private val cmd = Array("bash", "-c", "stty", "-a")
    private var lastFetched = System.currentTimeMillis()
    private var ttyWidth = fetchTTYWidth()

    override def width: Int = {
      val now = System.currentTimeMillis()
      if (now - lastFetched > 1000) {
        ttyWidth = fetchTTYWidth()
        lastFetched = now
      }
      ttyWidth
    }

    private def fetchTTYWidth(): Int = {
      val p = Runtime.getRuntime.exec(cmd)
      val sstyaOutput = Using.resource(new BufferedReader(new InputStreamReader(p.getInputStream))) { in =>
        val output = in.lines().toArray().mkString("\n")
        p.waitFor()
        output
      }
      regex1.findFirstMatchIn(sstyaOutput)
        .orElse(regex2.findFirstMatchIn(sstyaOutput))
        .orElse(regex3.findFirstMatchIn(sstyaOutput))
        .map(_.group(1).toInt)
        .getOrElse(DefaultTTYWidth.width)
    }
  }

  given default: TTYWidthProvider = TTYWidthFetcher
}

