package de.hpi.fgis.dendrotime.clustering.distances

object DistanceOptions {

  case class MSMOptions(cost: Double, window: Double, itakuraMaxSlope: Double) extends DistanceOptions

  case class DTWOptions(window: Double, itakuraMaxSlope: Double) extends DistanceOptions

  case class SBDOptions(standardize: Boolean, localFftwCacheSize: Option[Int] = None) extends DistanceOptions

  case class MinkowskyOptions(p: Int) extends DistanceOptions

  case class LorentzianOptions(normalize: Boolean) extends DistanceOptions

  case class KDTWOptions(gamma: Double, epsilon: Double, normalizeInput: Boolean, normalizeDistance: Boolean)
    extends DistanceOptions

  given MSMOptions(using opt: AllDistanceOptions): DistanceOptions.MSMOptions = opt.msm

  given DTWOptions(using opt: AllDistanceOptions): DistanceOptions.DTWOptions = opt.dtw

  given SBDOptions(using opt: AllDistanceOptions): DistanceOptions.SBDOptions = opt.sbd

  given MinkowskyOptions(using opt: AllDistanceOptions): DistanceOptions.MinkowskyOptions = opt.minkowsky

  given LorentzianOptions(using opt: AllDistanceOptions): DistanceOptions.LorentzianOptions = opt.lorentzian

  given KDTWOptions(using opt: AllDistanceOptions): DistanceOptions.KDTWOptions = opt.kdtw

  case class AllDistanceOptions(
                                 msm: DistanceOptions.MSMOptions,
                                 dtw: DistanceOptions.DTWOptions,
                                 sbd: DistanceOptions.SBDOptions,
                                 minkowsky: DistanceOptions.MinkowskyOptions,
                                 lorentzian: DistanceOptions.LorentzianOptions,
                                 kdtw: DistanceOptions.KDTWOptions
                               )
}

trait DistanceOptions
