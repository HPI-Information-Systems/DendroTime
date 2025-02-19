package de.hpi.fgis.dendrotime.io

private[io] object TsMetadata {
  private[io] def apply(metadata: (String, String)*): TsMetadata =
    apply(metadata.toMap)

  private[io] def apply(metadata: Map[String, String]): TsMetadata = {
    val canonicalMetadata = metadata.map {
      case (key, value) => key.toLowerCase.strip().replace("-", "").replace("_", "") -> value
    }
    val problemName = canonicalMetadata.getOrElse("problemname", "unknown")
    val timestamps = canonicalMetadata.getOrElse("timestamps", "false").toBoolean
    val missing = canonicalMetadata.getOrElse("missing", "false").toBoolean
    val univariate = canonicalMetadata.getOrElse("univariate", "true").toBoolean
    val dimension = canonicalMetadata.getOrElse("dimension", if (univariate) "1" else "0").toInt
    val equalLength = canonicalMetadata.getOrElse("equallength", "true").toBoolean
    val seriesLength = canonicalMetadata.getOrElse("serieslength", "0").toLong
    if canonicalMetadata.contains("targetlabel") then
      TsRegressionMetadata(problemName, timestamps, missing, univariate, dimension, equalLength, seriesLength, true)
    else if canonicalMetadata.contains("classlabel") then
      TsClassificationMetadata(problemName, timestamps, missing, univariate, dimension, equalLength, seriesLength, true)
    else
      throw new IllegalArgumentException("Missing target or class label in metadata!")
  }
}

/** Metadata about a TS file.
 *
 * TS files can either represent regression or classification problem.
 * */
sealed trait TsMetadata {
  def problemName: String

  def timestamps: Boolean

  def missing: Boolean

  def univariate: Boolean

  def dimension: Int

  def equalLength: Boolean

  def seriesLength: Long
}

final case class TsRegressionMetadata(
                                       problemName: String,
                                       timestamps: Boolean,
                                       missing: Boolean,
                                       univariate: Boolean,
                                       dimension: Int,
                                       equalLength: Boolean,
                                       seriesLength: Long,
                                       targetLabel: Boolean
                                     ) extends TsMetadata

final case class TsClassificationMetadata(
                                           problemName: String,
                                           timestamps: Boolean,
                                           missing: Boolean,
                                           univariate: Boolean,
                                           dimension: Int,
                                           equalLength: Boolean,
                                           seriesLength: Long,
                                           classLabel: Boolean
                                         ) extends TsMetadata
