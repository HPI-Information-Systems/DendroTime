package de.hpi.fgis.dendrotime.structures.strategies

import scala.math.Ordered.orderingToOrdered

trait FCFSMixin[T: Numeric] {
  this: WorkGenerator[T] =>
  protected val tsIds: IndexedSeq[T]

  private var i = 0
  private var j = 1
  private var count = 0

  override def sizeIds: Int = tsIds.size

  override def sizeTuples: Int = tsIds.size * (tsIds.size - 1) / 2

  override def index: Int = count

  override def hasNext: Boolean = i < tsIds.size - 1 && j < tsIds.size

  override def next(): (T, T) = {
    if !hasNext then
      throw new NoSuchElementException(s"GrowableFCFSWorkGenerator has no (more) work {i=$i, j=$j, ids=${tsIds.size}}")
    else
      var result = (tsIds(i), tsIds(j))
      if result._2 < result._1 then
        result = result.swap
      i += 1
      if i == j then
        i = 0
        j += 1
      count += 1
      result
  }

  override def knownSize: Int = tsIds.size * (tsIds.size - 1) / 2
}
