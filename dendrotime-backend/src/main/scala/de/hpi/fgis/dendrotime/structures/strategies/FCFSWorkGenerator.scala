package de.hpi.fgis.dendrotime.structures.strategies

object FCFSWorkGenerator {
  def apply[T : Numeric](tsIds: IndexedSeq[T]): WorkGenerator[T] = new FCFSWorkGenerator(tsIds)
}

class FCFSWorkGenerator[T : Numeric](override protected val tsIds: IndexedSeq[T])
  extends WorkGenerator[T] with FCFSMixin[T]
