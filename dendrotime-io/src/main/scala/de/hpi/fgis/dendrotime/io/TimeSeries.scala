package de.hpi.fgis.dendrotime.io


sealed trait TimeSeries {
  def id: Int

  def idx: Int

  def data: Array[Double]
}

object TimeSeries {
  final case class RawTimeSeries(id: Int, idx: Int, data: Array[Double]) extends TimeSeries

  final case class LabeledTimeSeries(id: Int, idx: Int, data: Array[Double], label: String) extends TimeSeries

  final given Ordering[TimeSeries] = Ordering.by(_.id)
}
