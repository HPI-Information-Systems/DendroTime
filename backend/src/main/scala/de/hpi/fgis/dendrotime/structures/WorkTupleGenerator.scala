package de.hpi.fgis.dendrotime.structures

import scala.collection.{AbstractIterator, mutable}

class WorkTupleGenerator extends AbstractIterator[(Long, Long)] with mutable.Growable[Long] {

  private val ids = mutable.ArrayBuffer.empty[Long]
  private var i = 0
  private var j = i + 1
  private var count = 0

  /** Resizes the internal buffer for the IDs. */
  def sizeHint(n: Int): Unit = ids.sizeHint(n)

  def sizeIds: Int = ids.size

  def sizeTuples: Int = ids.size * (ids.size - 1) / 2

  def index: Int = count

  override def addOne(tsId: Long): this.type = {
    ids += tsId
    this
  }

  override def addAll(tsIds: IterableOnce[Long]): this.type = {
    ids ++= tsIds
    this
  }

  override def hasNext: Boolean = i < ids.length - 1 && j < ids.length

  override def next(): (Long, Long) = {
    if !hasNext then
      throw new NoSuchElementException(s"WorkTupleGenerator has no (more) work {i=$i, j=$j, ids=$ids}")
    else
      val result = (ids(i), ids(j))
      inc()
      result
  }

  override def clear(): Unit = {
    ids.clear()
    i = 0
    j = i + 1
    count = 0
  }

  /** Known size is ambiguous because we have n time series IDs, but generate/iterate over n(n-1)/2 tuples. */
  override def knownSize: Int = -1

  def remaining: Int = sizeTuples - count

  def nextBatch(maxN: Int): Array[(Long, Long)] = {
    val n = Math.min(maxN, remaining)
    val batch = new Array[(Long, Long)](n)
    var k = 0
    while k < n do
      batch(k) = ids(i) -> ids(j)
      inc()
      k += 1
    batch
  }

  @inline
  private def inc(): Unit = {
    count += 1
    i += 1
    if i == j then
      i = 0
      j += 1
  }
}
