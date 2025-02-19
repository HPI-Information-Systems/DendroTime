package de.hpi.fgis.dendrotime.structures.strategies

import scala.collection.mutable
import scala.math.Ordered.orderingToOrdered

trait FCFSMixin[T: Numeric] { this: WorkGenerator[T] =>
  protected val tsIds: IndexedSeq[T]

  protected var i = 0
  protected var j = 1
  protected var count = 0

  protected def inc(): Unit = {
    count += 1
    i += 1
    if i == j then
      i = 0
      j += 1
  }

  override def sizeIds: Int = tsIds.size

  override def sizeTuples: Int = tsIds.size * (tsIds.size - 1) / 2

  override def index: Int = count

  override def hasNext: Boolean = i < tsIds.size - 1 && j < tsIds.size

  override def next(): (T, T) = {
    if !hasNext then
      throw new NoSuchElementException(s"WorkGenerator has no (more) work {i=$i, j=$j, ids=${tsIds.size}}")
    else
      var result = (tsIds(i), tsIds(j))
      if result._2 < result._1 then
        result = result.swap
      inc()
      result
  }

  override def nextBatch(maxN: Int, ignore: ((T, T)) => Boolean): Array[(T, T)] = {
    val batch = mutable.ArrayBuilder.make[(T, T)]
    batch.sizeHint(maxN)
    while batch.length < maxN && hasNext do
      val pair = tsIds(i) -> tsIds(j)
      if !ignore(pair) && !ignore(pair.swap) then
        batch += pair
      inc()
    batch.result()
  }

  override def knownSize: Int = tsIds.size * (tsIds.size - 1) / 2
}
