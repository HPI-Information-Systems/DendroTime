package de.hpi.fgis.progress

trait ProgressBarFormat {
  def barStart: String

  def barCurrent: String

  def barRemain: String

  def barEnd: String

  def barCurrentN: String = barCurrent

  def barFullStart: String = barStart

  def barFullEnd: String = barEnd
}

object ProgressBarFormat {
  case object Default extends ProgressBarFormat {
    override val barStart = "["
    override val barCurrent = "="
    override val barCurrentN = ">"
    override val barRemain = "-"
    override val barEnd = "]"
  }

  case object FiraFont extends ProgressBarFormat {
    override val barStart = "\uEE00"
    override val barFullStart = "\uEE03"
    override val barCurrent = "\uEE04"
    override val barRemain = "\uEE01"
    override val barEnd = "\uEE02"
    override val barFullEnd = "\uEE05"
  }
}