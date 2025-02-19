package de.hpi.fgis.dendrotime.structures.strategies

import de.hpi.fgis.dendrotime.clustering.PDist

import scala.collection.mutable
import scala.reflect.ClassTag

object OldApproxDistanceWorkGenerator {
  enum Direction {
    case Ascending
    case Descending
  }

  // create queue in factory function to allow GC of input data
  def apply[T: Numeric : ClassTag](
                                    mapping: Map[T, Int],
                                    dists: PDist,
                                    direction: Direction
                                  ): WorkGenerator[T] = {
    val queue = createQueue(mapping, dists)
    new OldApproxDistanceWorkGenerator(queue, dists.n, direction)
  }

  private def createQueue[T: ClassTag](
                                        mapping: Map[T, Int],
                                        dists: PDist
                                      )(
                                        using ord: Numeric[T]
                                      ): Array[(T, T)] = {
    import scala.math.Ordered.orderingToOrdered
    val ids = mapping.keys.toArray
    val work = Array.ofDim[(Double, T, T)](dists.n * (dists.n - 1) / 2)
    var i = 0
    var k = 0
    while i < ids.length - 1 do
      var j = i + 1
      while j < ids.length do
        var pair = ids(i) -> ids(j)
        if pair._2 < pair._1 then
          pair = pair.swap
        val distance = dists(mapping(pair._1), mapping(pair._2))
        work(k) = (distance, pair._1, pair._2)
        k += 1
        j += 1
      i += 1
    work.sortInPlaceBy(_._1)
    work.map(t => (t._2, t._3))
  }
}

class OldApproxDistanceWorkGenerator[T] private(
                                                 queue: Array[(T, T)],
                                                 n: Int,
                                                 direction: OldApproxDistanceWorkGenerator.Direction
                                               ) extends WorkGenerator[T] {
  private var i = 0

  override def sizeIds: Int = n

  override def sizeTuples: Int = queue.length

  override def index: Int = i

  override def hasNext: Boolean = i < queue.length

  override def next(): (T, T) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"OldApproxDistanceWorkGenerator has no (more) work {i=$i, queue.length=${queue.length}}"
      )
    else
      val result =
        if direction == OldApproxDistanceWorkGenerator.Direction.Ascending then queue(i)
        else queue(queue.length - i - 1)
      i += 1
      result
  }

  override def nextBatch(maxN: Int): Array[(T, T)] = {
    val n = Math.min(maxN, remaining)
    val batch =
      if direction == OldApproxDistanceWorkGenerator.Direction.Ascending then queue.slice(i, i + n)
      else queue.slice(queue.length - i - n - 1, queue.length - i)
    i += n
    batch
  }

  override def nextBatch(maxN: Int, ignore: ((T, T)) => Boolean): Array[(T, T)] = {
    val buf = mutable.ArrayBuilder.make[(T, T)]
    buf.sizeHint(maxN)
    while buf.length < maxN && hasNext do
      val item =
        if direction == OldApproxDistanceWorkGenerator.Direction.Ascending then queue(i)
        else queue(queue.length - i - 1)
      if !ignore(item) then
        buf += item
      i += 1
    buf.result()
  }
}