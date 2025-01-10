package de.hpi.fgis.dendrotime.structures.strategies

import de.hpi.fgis.dendrotime.clustering.PDist

import scala.math.Ordered.orderingToOrdered
import scala.reflect.ClassTag

object ApproxDistanceWorkGenerator {
  enum Direction {
    case Ascending
    case Descending
  }

  // create queue in factory function to allow GC of input data
  def apply[T: Numeric : ClassTag](mapping: Map[T, Int],
                                   dists: PDist,
                                   direction: Direction): WorkGenerator[T] = {
    val (distsCopy, pivots) = createQueue(dists)
    val reverseMapping = Array.ofDim[T](mapping.size)
    mapping.foreach { (id, idx) =>
      reverseMapping(idx) = id
    }
    new ApproxDistanceWorkGenerator(distsCopy, pivots, reverseMapping, direction)
  }

  private def createQueue(dists: PDist): (PDist, Array[Double]) = {
    // create copy of distances and sort them
    val distances = dists.distances.clone()
    distances.sortInPlace()

    // extract pivots to create log2(n * (n-1) / 2) bins
    val nBins = (Math.log(distances.length) / Math.log(2)).toInt
    val binSize = distances.length / nBins
    val pivots = Array.ofDim[Double](nBins + 1)
    pivots(0) = Double.MinValue
    var i = 1
    while i < nBins do
      pivots(i) = distances(i * binSize)
      i += 1
    pivots(pivots.length - 1) = Double.MaxValue
//    println(s"Pivots (pivots=$nBins, segments=${nBins-1} size=$binSize): ${pivots.mkString(", ")}")

    // create copy of the distances to freeze the contents
    Array.copy(dists.distances, 0, distances, 0, dists.length)

    // return both
    PDist.unsafeWrapArray(distances, dists.n) -> pivots
  }
}

class ApproxDistanceWorkGenerator[T: Numeric] private(
                                                       dists: PDist,
                                                       pivots: Array[Double],
                                                       reverseMapping: Array[T],
                                                       direction: ApproxDistanceWorkGenerator.Direction
                                                     ) extends WorkGenerator[T] {
  private var count = 0
//  private var lastCount = 0

  private var i = 0
  private var j = 1
  private var iPivot =
    if direction == ApproxDistanceWorkGenerator.Direction.Ascending then 0
    else pivots.length
  nextPivot()

  override def sizeIds: Int = dists.n

  override def sizeTuples: Int = dists.length

  override def index: Int = count

  override def hasNext: Boolean = index < sizeTuples

  override def next(): (T, T) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"ApproxDistanceWorkGenerator has no (more) work ($index / $sizeTuples)"
      )
    else
      while
        val d = dists(i, j)
        d < pivots(iPivot - 1) || d >= pivots(iPivot)
      do
        inc()
//      println(s"($i, $j) -> ${pivots(iPivot-1)} <= ${dists(i, j)} < ${pivots(iPivot)}")
      val (id1, id2) = reverseMapping(i) -> reverseMapping(j)
      inc()
      count += 1
      if id2 < id1 then
        id2 -> id1
      else
        id1 -> id2
  }

  private def inc(): Unit = {
    j += 1
    if j == dists.n then
      i += 1
      j = i + 1
    if i == dists.n - 1 then
      nextPivot()
  }

  private def nextPivot(): Unit = {
    if direction == ApproxDistanceWorkGenerator.Direction.Ascending then
      iPivot += 1
      i = 0
      j = 1
    else
      iPivot -= 1
      i = 0
      j = 1
//    if iPivot - 1 >= 0 && iPivot < pivots.length then
//      println(s"New segment (${iPivot-1}) ${iPivot-1} - $iPivot: (${pivots(iPivot - 1)}, ${pivots(iPivot)}] ($index/$sizeTuples, last size=${count - lastCount})")
//      lastCount = count
  }

//  override def nextBatch(maxN: Int): Array[(T, T)] = {
//    val n = Math.min(maxN, remaining)
//    val batch =
//      if direction == ApproxDistanceWorkGenerator.Direction.Ascending then queue.slice(count, count + n)
//      else queue.slice(queue.length - count - n - 1, queue.length - count)
//    count += n
//    batch
//  }
//
//  override def nextBatch(maxN: Int, ignore: ((T, T)) => Boolean): Array[(T, T)] = {
//    val buf = mutable.ArrayBuilder.make[(T, T)]
//    buf.sizeHint(maxN)
//    while buf.length < maxN && hasNext do
//      val item =
//        if direction == ApproxDistanceWorkGenerator.Direction.Ascending then queue(count)
//        else queue(queue.length - count - 1)
//      if !ignore(item) then
//        buf += item
//      count += 1
//    buf.result()
//  }
}
