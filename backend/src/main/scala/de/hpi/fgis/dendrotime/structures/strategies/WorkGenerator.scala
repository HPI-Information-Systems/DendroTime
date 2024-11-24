package de.hpi.fgis.dendrotime.structures.strategies

import scala.collection.AbstractIterator

trait WorkGenerator[T] extends AbstractIterator[(T, T)] {

  def sizeIds: Int

  def sizeTuples: Int

  def index: Int 

  def nextBatch(maxN: Int): Array[(T, T)]

  def remaining: Int = sizeTuples - index
}
