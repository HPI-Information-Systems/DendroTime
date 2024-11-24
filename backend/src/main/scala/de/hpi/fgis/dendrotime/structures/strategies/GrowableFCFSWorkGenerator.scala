package de.hpi.fgis.dendrotime.structures.strategies

import scala.collection.{AbstractIterator, mutable}
import scala.reflect.ClassTag

object GrowableFCFSWorkGenerator {
  def empty[T: ClassTag]: GrowableFCFSWorkGenerator[T] = new GrowableFCFSWorkGenerator

  def apply[T: ClassTag](initialTsIds: IterableOnce[T]): GrowableFCFSWorkGenerator[T] = {
    val generator = new GrowableFCFSWorkGenerator
    generator.addAll(initialTsIds)
  }

  def apply[T: ClassTag](initialTsIds: Seq[T]): GrowableFCFSWorkGenerator[T] = {
    val generator = new GrowableFCFSWorkGenerator
    generator.sizeHint(initialTsIds.size)
    generator.addAll(initialTsIds)
  }

  def ofExpectedSize[T: ClassTag](n: Int): GrowableFCFSWorkGenerator[T] = {
    val generator = new GrowableFCFSWorkGenerator
    generator.sizeHint(n)
    generator
  }
}

class GrowableFCFSWorkGenerator[T: ClassTag] extends WorkGenerator[T] with mutable.Growable[T] {

  private val ids = mutable.ArrayBuffer.empty[T]
  private var i = 0
  private var j = 1
  private var count = 0

  @inline
  private def inc(): Unit = {
    count += 1
    i += 1
    if i == j then
      i = 0
      j += 1
  }

  /** Resizes the internal buffer for the IDs. */
  def sizeHint(n: Int): Unit = ids.sizeHint(n)

  override def sizeIds: Int = ids.size

  override def sizeTuples: Int = ids.size * (ids.size - 1) / 2

  override def index: Int = count

  override def addOne(tsId: T): this.type = {
    ids += tsId
    this
  }

  override def addAll(tsIds: IterableOnce[T]): this.type = {
    ids ++= tsIds
    this
  }

  override def hasNext: Boolean = i < ids.length - 1 && j < ids.length

  override def next(): (T, T) = {
    if !hasNext then
      throw new NoSuchElementException(s"GrowableFCFSWorkGenerator has no (more) work {i=$i, j=$j, ids=$ids}")
    else
      val result = ids(i) -> ids(j)
      inc()
      result
  }

  override def nextBatch(maxN: Int): Array[(T, T)] = {
    val n = Math.min(maxN, remaining)
    val batch = new Array[(T, T)](n)
    var k = 0
    while k < n do
      batch(k) = ids(i) -> ids(j)
      inc()
      k += 1
    batch
  }

  override def clear(): Unit = {
    ids.clear()
    i = 0
    j = 1
    count = 0
  }

  /** Known size is ambivalent because we have n time series IDs, but generate/iterate over n(n-1)/2 tuples. */
  override def knownSize: Int = -1
}
