package de.hpi.fgis.dendrotime.structures.strategies

import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.structures.MeanErrorTracker

import scala.collection.mutable
import scala.math.Ordered.orderingToOrdered
import scala.reflect.ClassTag
import scala.util.boundary
import scala.util.boundary.break

object ApproxFullErrorWorkGenerator {

  def apply[T: Numeric : ClassTag](mapping: Map[T, Int]): ApproxFullErrorWorkGenerator[T] = {
    val maxIdx = mapping.values.max
    val idMap = Array.ofDim[T](maxIdx + 1)
    mapping.foreach { case (tsId, idx) => idMap(idx) = tsId }
    new ApproxFullErrorWorkGenerator[T](idMap.indices.toArray, idMap)
  }
}

class ApproxFullErrorWorkGenerator[T: Numeric : ClassTag](tsIds: Array[Int],
                                                          idMap: Array[T]) extends WorkGenerator[T] {
  // memory consumption:
  // - idMap: n * Long = 8n bytes
  // - tsIds: n * Int = 4n bytes
  // - errors: n * Double + n * Int = 12n bytes
  // - processed: n bits = 1/8n bytes
  // Summary: 25n bytes (plus overhead)
  // --> way better than all strategies that hold the work queue in memory: (8 + 8 + 32) * n * (n-1) / 2 bytes
  // runtime:
  // - m * O(n log n) sorting of the IDs
  // - m * O(n²) for searching the next unprocessed pair
  // - m * O(1) for updating the errors, processed set, and returning the next pair
  // --> O(m * n²) = O(n * (n-1) * n²) = O(n^4) for the whole process :(
  private val n = idMap.length
  private val m = n * (n - 1) / 2
  private val errors = MeanErrorTracker(n)
  private val processed = new mutable.BitSet(m)
  private var count = 0
  private var sortNecessary = false

  def updateError(i: Int, j: Int, error: Double): Unit = {
    val absError = Math.abs(error)
    errors.update(i, absError)
    errors.update(j, absError)
    sortNecessary = true
  }

  def updateErrors(newErrors: Map[(Int, Int), Double]): Unit = {
    newErrors.foreach { case ((i, j), error) =>
      updateError(i, j, error)
    }
  }

  override def sizeIds: Int = n

  override def sizeTuples: Int = m

  override def index: Int = count

  override def hasNext: Boolean = count < m

  override def next(): (T, T) = {
    if !hasNext then
      throw new NoSuchElementException(s"GrowableFCFSWorkGenerator has no (more) work {count=$count, processed=${processed.size}, ids=$n}")

    // find next non-processed tuple with the largest error involving the top-error time series
    if sortNecessary then
      tsIds.sortInPlaceBy(id => -errors(id))
      sortNecessary = false
    var i = 0
    var j = i + 1
    while wasProcessed(tsIds(i), tsIds(j)) do
      j += 1
      if j == n then
        i += 1
        j = i + 1
    val lowerBound = meanError(tsIds(i), tsIds(j))

    // check if there is a pair with a larger error
    var k = i + 1
    var l = k + 1
    boundary {
      while k < j && l <= j do
        val pair = tsIds(k) -> tsIds(l)
        if meanError(pair) <= lowerBound then
          // all other pairs have a smaller error --> use the lower bound
          break()
        if !wasProcessed(pair) then
          // we found a larger error than the lower bound --> use it
          i = k
          j = l
          break()

        if l == j then
          k += 1
          l = k + 1
        else
          l += 1
    }

    // get the time series indices, update the processed set, and map to target IDs
    val pair = (tsIds(i), tsIds(j))
    setProcessed(pair)
    count += 1
    var result = (idMap(pair._1), idMap(pair._2))
    if result._2 < result._1 then
      result = result.swap
    result
  }

  override def knownSize: Int = m

  @inline
  private def setProcessed(p: (Int, Int)): Unit =
    val k = if p._1 <= p._2 then PDist.index(p._1, p._2, n) else PDist.index(p._2, p._1, n)
    processed += k

  @inline
  private def wasProcessed(p: (Int, Int)): Boolean =
    val k = if p._1 <= p._2 then PDist.index(p._1, p._2, n) else PDist.index(p._2, p._1, n)
    processed.contains(k)

  @inline
  private def meanError(p: (Int, Int)): Double =
    meanError(p._1, p._2)

  @inline
  private def meanError(id1: Int, id2: Int): Double =
    (errors(id1) + errors(id2)) / 2
}

@main
def main(): Unit = {
  val approxDists = PDist(Array(
    Array(0.0, 6.925, 8.9055, 6.116999999999999, 7.559),
    Array(6.925, 0.0, 1.9804999999999997, 0.9229999999999999, 0.7100000000000001),
    Array(8.9055, 1.9804999999999997, 0.0, 2.7885, 1.3464999999999998),
    Array(6.116999999999999, 0.9229999999999999, 2.7885, 0.0, 1.5179999999999998),
    Array(7.559, 0.7100000000000001, 1.3464999999999998, 1.5179999999999998, 0.0)
  ), 5)
  val dists = PDist(Array(
    Array(0.0, 68.33999999999999, 80.89099999999996, 82.70599999999992, 98.92599999999997),
    Array(68.33999999999999, 0.0, 26.53900000000001, 52.376000000000005, 46.347999999999985),
    Array(80.89099999999996, 26.53900000000001, 0.0, 54.303, 46.727),
    Array(82.70599999999992, 52.376000000000005, 54.303, 0.0, 43.83600000000002),
    Array(98.92599999999997, 46.347999999999985, 46.727, 43.83600000000002, 0.0)
  ), 5)

  def executeDynamicStrategy(mapping: Map[Int, Int]): Array[(Int, Int)] = {
    val order = mutable.ArrayBuilder.make[(Int, Int)]
    val strategy = ApproxFullErrorWorkGenerator(mapping)

    while strategy.hasNext do
      val nextPair = strategy.next()

      //    println(s"Processing pair $nextPair")
      val (i, j) = nextPair
      val dist = dists(i, j)
      val error = approxDists(i, j) - dist
      strategy.updateError(i, j, error)
      order += nextPair

    order.result()
  }

  // compute all orderings
  val mapping = Map(0 -> 0, 1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4)
  val dynamicError = executeDynamicStrategy(mapping)
  print(dynamicError.mkString(", "))
}
