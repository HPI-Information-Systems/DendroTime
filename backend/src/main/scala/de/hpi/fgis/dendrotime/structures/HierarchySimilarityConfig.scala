package de.hpi.fgis.dendrotime.structures

import com.typesafe.config.Config

sealed trait HierarchySimilarityConfig {
  def name: String
}

object HierarchySimilarityConfig {
  case class AriAt(k: Int) extends HierarchySimilarityConfig {
    override val name: String = "ariAt"
  }
  case class AmiAt(k: Int) extends HierarchySimilarityConfig {
    override val name: String = "amiAt"
  }
  case class LabelChangesAt(k: Option[Int]) extends HierarchySimilarityConfig {
    override def name: String = "labelChangesAt"
  }
  case object AverageAri extends HierarchySimilarityConfig {
    override def name: String = "averageAri"
  }
  case class ApproxAverageAri(factor: Double) extends HierarchySimilarityConfig {
    override def name: String = "approxAverageAri"
  }

  sealed trait WithBf extends HierarchySimilarityConfig {
    def useBf: Boolean = true
  }
  case class HierarchySimilarityWithBf(cardLowerBound: Int, cardUpperBound: Int) extends WithBf {
    override def name: String = "hierarchySimilarity"
  }
  case object WeightedHierarchySimilarityWithBf extends WithBf {
    override def name: String = "weightedHierarchySimilarity"
  }

  sealed trait WithBitset extends HierarchySimilarityConfig {
    def useBf: Boolean = false
  }
  case class HierarchySimilarityWithBitset(cardLowerBound: Int, cardUpperBound: Int) extends WithBitset {
    override def name: String = "hierarchySimilarity"
  }
  case object WeightedHierarchySimilarityWithBitset extends WithBitset {
    override def name: String = "weightedHierarchySimilarity"
  }

  extension (c: Config) {
    private def getIntOption(str: String): Option[Int] = if c.hasPath(str) then Some(c.getInt(str)) else None
  }

  def fromConfig(config: Config): HierarchySimilarityConfig = {
    val name = config.getString("name")
    name match {
      case "ariAt" => AriAt(config.getInt("k"))
      case "amiAt" => AmiAt(config.getInt("k"))
      case "labelChangesAt" => LabelChangesAt(config.getIntOption("k"))
      case "averageAri" => AverageAri
      case "approxAverageAri" => ApproxAverageAri(config.getDouble("factor"))
      case "hierarchySimilarity" =>
        val useBf = config.getBoolean("use-bloom-filters")
        val lb = config.getInt("cardinality-lower-bound")
        val ub = config.getInt("cardinality-upper-bound")
        if useBf then HierarchySimilarityWithBf(lb, ub) else HierarchySimilarityWithBitset(lb, ub)
      case "weightedHierarchySimilarity" =>
        val useBf = config.getBoolean("use-bloom-filters")
        if useBf then WeightedHierarchySimilarityWithBf else WeightedHierarchySimilarityWithBitset
    }
  }
}
