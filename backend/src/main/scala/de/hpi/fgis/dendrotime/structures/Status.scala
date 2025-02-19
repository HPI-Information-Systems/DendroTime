package de.hpi.fgis.dendrotime.structures


sealed trait Status

object Status {
  case object Initializing extends Status

  case object Approximating extends Status

  case object ComputingFullDistances extends Status

  case object Finalizing extends Status

  case object Finished extends Status

  final given Ordering[Status] = Ordering.by {
    case Initializing => 0
    case Approximating => 1
    case ComputingFullDistances => 2
    case Finalizing => 3
    case Finished => 4
  }
}
