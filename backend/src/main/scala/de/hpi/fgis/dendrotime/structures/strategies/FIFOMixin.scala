package de.hpi.fgis.dendrotime.structures.strategies

trait FIFOMixin[T: Numeric] extends FCFSMixin[T] { this: WorkGenerator[T] =>
  override protected def inc(): Unit = {
    count += 1
    j += 1
    if j == tsIds.size then
      i += 1
      j = i + 1
  }
}
