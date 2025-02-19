package de.hpi.fgis.dendrotime.structures.strategies

import de.hpi.fgis.dendrotime.structures.MeanErrorTracker
import org.apache.commons.math3.util.FastMath

import scala.collection.mutable
import scala.math.Ordered.orderingToOrdered
import scala.reflect.ClassTag

object ApproxFullErrorWorkGenerator {

  def apply[T: Numeric : ClassTag](mapping: Map[T, Int]): ApproxFullErrorWorkGenerator[T] = {
    val maxIdx = mapping.values.max
    val idMap = Array.ofDim[T](maxIdx + 1)
    mapping.foreach { case (tsId, idx) => idMap(idx) = tsId }
    new ApproxFullErrorWorkGenerator[T](idMap.indices.toArray, idMap)
  }
}

/**
 * While computing the full distances, tracks the absolute error between the new full distance and the existing
 * approximate distance. The absolute error is tracked for all time series individually. A single computation, thus,
 * updates the error of two time series. The time series are, then, sorted by their mean error so far and the next
 * TS pair with the largest error is the next job.
 * */
class ApproxFullErrorWorkGenerator[T: Numeric : ClassTag](tsIds: Array[Int], idMap: Array[T])
  extends WorkGenerator[T] with TsErrorMixin(tsIds.length, tsIds.length * (tsIds.length - 1) / 2) {
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
  private val tracker = MeanErrorTracker(n)
  override protected val errors: scala.collection.IndexedSeq[Double] = tracker
  private var count = 0
  private var sortNecessary = false

  def updateError(i: Int, j: Int, error: Double): Unit = {
    val absError = FastMath.abs(error)
    tracker.update(i, absError)
    tracker.update(j, absError)
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
      throw new NoSuchElementException(s"ApproxFullErrorWorkGenerator has no (more) work {processed=$count/$m, ids=$n}")

    if sortNecessary then
      tsIds.sortInPlaceBy(id => -errors(id))
      sortNecessary = false

    val nextPair = nextLargestErrorPair(tsIds)
    count += 1
    var result = (idMap(nextPair._1), idMap(nextPair._2))
    if result._2 < result._1 then
      result = result.swap
    result
  }

  override def nextBatch(maxN: Int, ignore: ((T, T)) => Boolean): Array[(T, T)] = {
    if sortNecessary then
      tsIds.sortInPlaceBy(id => -errors(id))
      sortNecessary = false

    val buf = mutable.ArrayBuilder.make[(T, T)]
    buf.sizeHint(maxN)
    while buf.length < n && hasNext do
      val pair = nextLargestErrorPair(tsIds)
      var mappedPair = (idMap(pair._1), idMap(pair._2))
      if mappedPair._2 < mappedPair._1 then
        mappedPair = mappedPair.swap
      if !ignore(mappedPair) then
        buf += mappedPair
    count += n
    buf.result()
  }

  override def knownSize: Int = m
}

//@main
//def main(): Unit = {
//  val approxDists = PDist(Array(
//    Array(0.0, 6.925, 8.9055, 6.116999999999999, 7.559),
//    Array(6.925, 0.0, 1.9804999999999997, 0.9229999999999999, 0.7100000000000001),
//    Array(8.9055, 1.9804999999999997, 0.0, 2.7885, 1.3464999999999998),
//    Array(6.116999999999999, 0.9229999999999999, 2.7885, 0.0, 1.5179999999999998),
//    Array(7.559, 0.7100000000000001, 1.3464999999999998, 1.5179999999999998, 0.0)
//  ), 5)
//  val dists = PDist(Array(
//    Array(0.0, 68.33999999999999, 80.89099999999996, 82.70599999999992, 98.92599999999997),
//    Array(68.33999999999999, 0.0, 26.53900000000001, 52.376000000000005, 46.347999999999985),
//    Array(80.89099999999996, 26.53900000000001, 0.0, 54.303, 46.727),
//    Array(82.70599999999992, 52.376000000000005, 54.303, 0.0, 43.83600000000002),
//    Array(98.92599999999997, 46.347999999999985, 46.727, 43.83600000000002, 0.0)
//  ), 5)
//
//  def executeDynamicStrategy(mapping: Map[Int, Int]): Array[(Int, Int)] = {
//    val order = mutable.ArrayBuilder.make[(Int, Int)]
//    val strategy = ApproxFullErrorWorkGenerator(mapping)
//
//    while strategy.hasNext do
//      val nextPair = strategy.next()
//
//      //    println(s"Processing pair $nextPair")
//      val (i, j) = nextPair
//      val dist = dists(i, j)
//      val error = approxDists(i, j) - dist
//      strategy.updateError(i, j, error)
//      order += nextPair
//
//    order.result()
//  }
//
//  // compute all orderings
//  val mapping = Map(0 -> 0, 1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4)
//  val dynamicError = executeDynamicStrategy(mapping)
//  print(dynamicError.mkString(", "))
//}
