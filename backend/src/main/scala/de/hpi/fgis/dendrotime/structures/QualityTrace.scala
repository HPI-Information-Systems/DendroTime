package de.hpi.fgis.dendrotime.structures

import scala.collection.mutable

final case class QualityTrace private(
                                       indices: Seq[Int],
                                       timestamps: Seq[Long],
                                       similarities: Seq[Double],
                                       gtSimilarities: Seq[Double],
                                       clusterQualities: Seq[Double],
                                     ) {
  def hasGtSimilarities: Boolean = gtSimilarities.nonEmpty

  def hasClusterQualities: Boolean = clusterQualities.nonEmpty

  def size: Int = indices.length
}

object QualityTrace {
  def newBuilder: QualityTraceBuilder = new QualityTraceBuilder

  def empty: QualityTrace = QualityTrace(
    indices = Seq.empty,
    timestamps = IndexedSeq.empty,
    similarities = IndexedSeq.empty,
    gtSimilarities = IndexedSeq.empty,
    clusterQualities = IndexedSeq.empty
  )

  final class QualityTraceBuilder private[QualityTrace] {
    private val nComputations: mutable.ArrayBuffer[Int] = mutable.ArrayBuffer.empty
    private val timestamps: mutable.ArrayBuffer[Long] = mutable.ArrayBuffer.empty
    private val similarities: mutable.ArrayBuffer[Double] = mutable.ArrayBuffer.empty
    private val gtSimilarities: mutable.ArrayBuffer[Double] = mutable.ArrayBuffer.empty
    private val clusterQualities: mutable.ArrayBuffer[Double] = mutable.ArrayBuffer.empty

    def addStep(index: Int, similarity: Double): this.type =
      addStep(index, System.currentTimeMillis(), similarity)

    def addStep(index: Int, timestamp: Long, similarity: Double): this.type = {
      nComputations += index
      timestamps += timestamp
      similarities += similarity
      this
    }

    def addStep(index: Int, similarity: Double, gtSimilarity: Double, clusterQuality: Double): this.type =
      addStep(index, System.currentTimeMillis(), similarity, gtSimilarity, clusterQuality)

    def addStep(index: Int, timestamp: Long, similarity: Double, gtSimilarity: Double, clusterQuality: Double): this.type = {
      fillMissingSimilarities(gtSimilarities)
      fillMissingSimilarities(clusterQualities)
      nComputations += index
      timestamps += timestamp
      similarities += similarity
      gtSimilarities += gtSimilarity
      clusterQualities += clusterQuality
      this
    }

    def withGtSimilarity(gtSimilarity: Double): this.type = {
      if nComputations.length == gtSimilarities.length then
        throw new IllegalStateException("Cannot add ground truth similarity without adding a new step first")
      fillMissingSimilarities(gtSimilarities, offset = -1)
      gtSimilarities += gtSimilarity
      this
    }

    def withClusterQuality(clusterQuality: Double): this.type = {
      if nComputations.length == clusterQualities.length then
        throw new IllegalStateException("Cannot add cluster quality without adding a new step first")
      fillMissingSimilarities(clusterQualities, offset = -1)
      clusterQualities += clusterQuality
      this
    }

    def result(): QualityTrace = QualityTrace(
      indices = nComputations.toArray,
      timestamps = timestamps.toArray,
      similarities =
        val x = similarities.toArray
        val max = x.max
        if max <= 1.0 then x
        else x.map(_ / max),
      gtSimilarities = gtSimilarities.toArray,
      clusterQualities = clusterQualities.toArray
    )

    def clear(): Unit = {
      nComputations.clear()
      timestamps.clear()
      similarities.clear()
      gtSimilarities.clear()
      clusterQualities.clear()
    }

    private def fillMissingSimilarities(simBuffer: mutable.ArrayBuffer[Double], offset: Int = 0): Unit = {
      if simBuffer.length < nComputations.length + offset then
        val missing = nComputations.length + offset - simBuffer.length
        simBuffer ++= Seq.fill(missing)(0.0)
    }
  }
}
