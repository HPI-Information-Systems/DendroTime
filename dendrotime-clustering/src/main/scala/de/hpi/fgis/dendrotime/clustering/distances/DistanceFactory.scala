package de.hpi.fgis.dendrotime.clustering.distances

trait DistanceFactory[T <: Distance, O <: DistanceOptions] {
  protected final val eps = Double.MinPositiveValue

  def create(using opt: O): T

}
