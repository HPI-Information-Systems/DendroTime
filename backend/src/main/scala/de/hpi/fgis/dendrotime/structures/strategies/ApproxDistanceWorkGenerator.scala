package de.hpi.fgis.dendrotime.structures.strategies

import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.structures.WelfordsAlgorithm
import org.apache.commons.math3.special.Erf
import org.apache.commons.math3.util.FastMath

import scala.math.Ordered.orderingToOrdered
import scala.reflect.ClassTag

object ApproxDistanceWorkGenerator {
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
    val (distsCopy, pivots) = createQueue(dists)
    val reverseMapping = Array.ofDim[T](mapping.size)
    mapping.foreach { (id, idx) =>
      reverseMapping(idx) = id
    }
    new ApproxDistanceWorkGenerator(distsCopy, pivots, reverseMapping, direction)
  }

  private def createQueue(dists: PDist): (PDist, Array[Double]) = {
    // create copy of the distances to freeze the contents
    val distances = dists.distances.clone()

    // extract pivots to create 3 * log2(n * (n-1) / 2) bins (must be even)
    val n = 3 * (Math.log(distances.length) / Math.log(2)).toInt
    val nBins = if n % 2 == 1 then n + 1 else n
    val pivots = extractPivotsQuantiles(distances, nBins)
//    println(s"Pivots (pivots=${nBins+1}, segments=$nBins, size=${distances.length / nBins}): ${pivots.mkString(", ")}")

    // return both
    PDist.unsafeWrapArray(distances) -> pivots
  }

  private def extractPivotsQuantiles(distances: Array[Double], nBins: Int): Array[Double] = {
    // estimate normal distribution parameters
    val stats = WelfordsAlgorithm(distances)
//    println(f"Stats: $stats, expected size=${distances.length / nBins} (${100.0/nBins}%.2f%%)")

    // create pivots based on normal distribution
    val pivots = Array.ofDim[Double](nBins + 1)
    var i = - nBins / 2
    var j = 0
    while i <= nBins / 2 do
      val p = 1.0 / nBins * i
      val x = customProbit(p)
      pivots(j) = stats.mean + x * stats.stdDev
      i += 1
      j += 1

    pivots
  }

  private def customProbit(x: Double): Double = Erf.erfInv(2 * x) * FastMath.sqrt(2.0)
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
      throw new NoSuchElementException(s"ApproxDistanceWorkGenerator has no (more) work ($index / $sizeTuples)")
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
}
