package de.hpi.fgis.dendrotime.clustering.distances

object DistanceOptions {
  case class MSMOptions(cost: Double, window: Double, itakuraMaxSlope: Double)

  case class DTWOptions(window: Double, itakuraMaxSlope: Double)

  case class SBDOptions(standardize: Boolean)

  case class MinkowskyOptions(p: Int)

  given MSMOptions(using opt: DistanceOptions): DistanceOptions.MSMOptions = opt.msm

  given DTWOptions(using opt: DistanceOptions): DistanceOptions.DTWOptions = opt.dtw

  given SBDOptions(using opt: DistanceOptions): DistanceOptions.SBDOptions = opt.sbd

  given MinkowskyOptions(using opt: DistanceOptions): DistanceOptions.MinkowskyOptions = opt.minkowsky
}

case class DistanceOptions(
                            msm: DistanceOptions.MSMOptions,
                            dtw: DistanceOptions.DTWOptions,
                            sbd: DistanceOptions.SBDOptions,
                            minkowsky: DistanceOptions.MinkowskyOptions
                          )
