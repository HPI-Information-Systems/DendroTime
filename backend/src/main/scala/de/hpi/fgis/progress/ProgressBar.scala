package de.hpi.fgis.progress

import scala.annotation.targetName
import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.reflect.ClassTag

object ProgressBar {
  enum DisplayUnit {
    case Default
    case Bytes
  }

  final case class Config(
    showBar: Boolean = true,
    showSpeed: Boolean = false,
    showPercent: Boolean = false,
    showCounter: Boolean = true,
    showTimeLeft: Boolean = true,
    unit: DisplayUnit = DisplayUnit.Default
  )

  val defaultConfig: Config = Config()

  def of(n: Int,
         format: ProgressBarFormat = ProgressBarFormat.Default,
         config: ProgressBar.Config = ProgressBar.defaultConfig
        )(using print: Output, ttyWidthProvider: TTYWidthProvider): ProgressBar = {
    new ProgressBar(n, format, config)
  }

  def apply[T: ClassTag](it: IterableOnce[T],
                         format: ProgressBarFormat = ProgressBarFormat.Default,
                         config: ProgressBar.Config = ProgressBar.defaultConfig
                        )(using print: Output, ttyWidthProvider: TTYWidthProvider): Iterator[T] = {
    val n = it.knownSize
    val bar = new ProgressBar(n, format, config)
    val iter = it.iterator
    new Iterator[T] {
      override def hasNext: Boolean = {
        val n = iter.hasNext
        if !n then bar.finish()
        n
      }
      override def next(): T = {
        bar.add(1)
        iter.next()
      }
    }
  }

  private def kbFmt(n: Double): String = {
    val kb = 1024
    n match {
      case x if x >= Math.pow(kb, 4) => "%.2f TB".format(x / Math.pow(kb, 4))
      case x if x >= Math.pow(kb, 3) => "%.2f GB".format(x / Math.pow(kb, 3))
      case x if x >= Math.pow(kb, 2) => "%.2f MB".format(x / Math.pow(kb, 2))
      case x if x >= kb => "%.2f KB".format(x / kb)
      case _ => "%.0f B".format(n)
    }
  }
}

class ProgressBar(n: Int,
                  format: ProgressBarFormat = ProgressBarFormat.Default,
                  config: ProgressBar.Config = ProgressBar.defaultConfig,
                 )(using print: Output, ttyWidthProvider: TTYWidthProvider) {

  import ProgressBar.*

  private val startTime = System.currentTimeMillis()
  private var current = 0
  private val unit = config.unit

  val total: Int = n
  val progress: Int = current

  def add(i: Int): Int = {
    current += i
    if (current <= total) draw()
    current
  }

  @targetName("plusEquals")
  def +=(i: Int): Int = add(i)

  private def draw(): Unit = {
    val width = ttyWidthProvider.width
    val prefix = new StringBuilder
    val base = new StringBuilder
    val suffix = new StringBuilder

    val now = System.currentTimeMillis()
    val fromStart = now - startTime

    // percent
    if config.showPercent then
      val percent = current * 100 / total.toFloat
      suffix.append(" %.2f %%".format(percent))

    // speed
    if config.showSpeed then
      val speed = current.toDouble / (fromStart / (1 seconds).toMillis)
      suffix.append(unit match {
        case DisplayUnit.Default => " %.0f/s".format(speed)
        case DisplayUnit.Bytes => " %s/s".format(ProgressBar.kbFmt(speed))
      })

    // time left
    if config.showTimeLeft then
      val left = (fromStart.toDouble / current) * (total - current)
      val dur = FiniteDuration(left.toLong, MILLISECONDS)
      suffix.append(" ").append(dur.toString)

    // counter
    if config.showCounter then
      prefix.append(unit match {
        case DisplayUnit.Default => "%d / %d ".format(current, total)
        case DisplayUnit.Bytes => "%s / %s ".format(ProgressBar.kbFmt(current), ProgressBar.kbFmt(total))
      })

    // bar box
    if config.showBar then
      val size = width - prefix.length + suffix.length - 3
      if (size > 0) {
        val curCount = Math.ceil((current.toFloat / total) * size).toInt
        val remCount = size - curCount
        if curCount > 0 then
          base.append(format.barFullStart)
        else
          base.append(format.barStart)

        if remCount > 0 then
          base.append(format.barCurrent * (curCount - 1)).append(format.barCurrentN)
        else
          base.append(format.barCurrent * curCount)
        base.append(format.barRemain * remCount)
        if remCount > 0 then
          base.append(format.barEnd)
        else
          base.append(format.barFullEnd)
      }

    // construct new line
    val out = new StringBuilder("\r")
      .append(prefix)
      .append(base)
      .append(suffix)
    if (out.length < width) then
      out.append(" " * (width - out.length))
    print("\r" + out)
  }

  def finish(): Unit = {
    if current < total then
      add(total - current)
    print(System.lineSeparator())
  }
}
