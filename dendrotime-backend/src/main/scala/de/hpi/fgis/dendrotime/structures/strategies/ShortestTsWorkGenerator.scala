package de.hpi.fgis.dendrotime.structures.strategies

import scala.collection.immutable.ArraySeq
import scala.reflect.ClassTag

object ShortestTsWorkGenerator {
  def apply[T : Numeric : ClassTag](lengths: Map[T, Int]): ShortestTsWorkGenerator[T] =
    new ShortestTsWorkGenerator(lengths)
}

class ShortestTsWorkGenerator[T : Numeric : ClassTag](lengths: Map[T, Int]) extends WorkGenerator[T] with FCFSMixin[T] {
  override protected val tsIds: IndexedSeq[T] = {
    val idLengths = lengths.iterator.toArray
    idLengths.sortInPlaceBy(_._2)
    ArraySeq.unsafeWrapArray(idLengths.map(_._1))
  }
}
