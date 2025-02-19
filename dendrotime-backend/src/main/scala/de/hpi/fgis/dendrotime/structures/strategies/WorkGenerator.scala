package de.hpi.fgis.dendrotime.structures.strategies

import scala.collection.{AbstractIterator, mutable}
import scala.reflect.ClassTag

object WorkGenerator {

  def empty[T](using ev: ClassTag[T]): WorkGenerator[T] = ev match
    case ClassTag.Int => EmptyIntGen
    case ClassTag.Long => EmptyLongGen
    case _ => new EmptyGen[T]

  private class EmptyGen[T] extends WorkGenerator[T] {

    override def sizeIds: Int = 0

    override def sizeTuples: Int = 0

    override def index: Int = 0

    override def next(): (T, T) = throw new NoSuchElementException("EmptyGen has no work")

    override def hasNext: Boolean = false
  }

  private val EmptyIntGen = new EmptyGen[Int]

  private val EmptyLongGen = new EmptyGen[Long]
}

trait WorkGenerator[T] extends AbstractIterator[(T, T)] {

  def sizeIds: Int

  def sizeTuples: Int

  def index: Int

  def next(): (T, T)

  def nextBatch(maxN: Int): Array[(T, T)] = nextBatch(maxN, _ => false)

  def nextBatch(maxN: Int, ignore: ((T, T)) => Boolean): Array[(T, T)] = {
    val buf = mutable.ArrayBuilder.make[(T, T)]
    buf.sizeHint(maxN)
    while buf.length < maxN && hasNext do
      val item = next()
      if !ignore(item) then
        buf += item
    buf.result()
  }

  def remaining: Int = sizeTuples - index
}
