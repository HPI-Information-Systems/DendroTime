package de.hpi.fgis.dendrotime.structures.strategies

import scala.collection.immutable.ArraySeq
import scala.math.Ordered.orderingToOrdered
import scala.reflect.ClassTag

object ApproxDiffTsErrorWorkGenerator {
  def apply[T: Numeric : ClassTag](tsApproxDiffs: Array[Double], mapping: Map[T, Int]): ApproxDiffTsErrorWorkGenerator[T] = {
    val data = tsApproxDiffs.zipWithIndex
    data.sortInPlaceBy(_._1)
    val tsIds = data.map(_._2).reverse
    val approxDiff = data.map(_._1).reverse
    val maxIdx = mapping.values.max
    val idMap = Array.ofDim[T](maxIdx + 1)
    mapping.foreach { case (tsId, idx) => idMap(idx) = tsId }
    new ApproxDiffTsErrorWorkGenerator(tsIds, approxDiff, idMap)
  }
}

class ApproxDiffTsErrorWorkGenerator[T: Numeric : ClassTag](tsIds: Array[Int], approxDiff: Array[Double], idMap: Array[T])
  extends WorkGenerator[T] with TsErrorMixin(tsIds.length, tsIds.length * (tsIds.length - 1) / 2) {

  override protected val errors: scala.collection.IndexedSeq[Double] = approxDiff
  private var i = 0

  override def sizeIds: Int = approxDiff.length

  override def sizeTuples: Int = sizeIds * (sizeIds - 1) / 2

  override def index: Int = i

  override def hasNext: Boolean = i < sizeTuples

  override def next(): (T, T) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"ApproxDiffTsErrorWorkGenerator has no (more) work {i=$i/$sizeTuples}"
      )

    val result = nextLargestErrorPair(ArraySeq.unsafeWrapArray(tsIds))
    i += 1
    val pair = (idMap(result._1), idMap(result._2))
    if pair._2 < pair._1 then
      pair.swap
    else
      pair
  }
}
