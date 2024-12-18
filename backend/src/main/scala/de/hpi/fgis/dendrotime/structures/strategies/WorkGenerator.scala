package de.hpi.fgis.dendrotime.structures.strategies

import scala.collection.{AbstractIterator, mutable}

trait WorkGenerator[T] extends AbstractIterator[(T, T)] {

  def sizeIds: Int

  def sizeTuples: Int

  def index: Int 

  def next(): (T, T)

  def nextBatch(maxN: Int): Array[(T, T)] = nextBatch(maxN, _ => false)

  def nextBatch(maxN: Int, ignore: ((T, T)) => Boolean): Array[(T, T)] =
    val buf = mutable.ArrayBuilder.make[(T, T)]
    buf.sizeHint(maxN)
    while buf.length < maxN && hasNext do
      val item = next()
      if !ignore(item) then
        buf += item
    buf.result()

  def remaining: Int = sizeTuples - index
}
