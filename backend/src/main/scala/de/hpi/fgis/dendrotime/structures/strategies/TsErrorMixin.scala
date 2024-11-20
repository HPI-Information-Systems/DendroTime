package de.hpi.fgis.dendrotime.structures.strategies

import de.hpi.fgis.dendrotime.clustering.PDist

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

trait TsErrorMixin(n: Int, m: Int) {
  private val processed = new mutable.BitSet(m)

  protected val errors: scala.collection.IndexedSeq[Double]

  protected def nextLargestErrorPair(tsIds: IndexedSeq[Int]): (Int, Int) = {
    // find next non-processed tuple with the largest error involving the top-error time series
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

    // we found the pair with the largest error
    val pair = (tsIds(i), tsIds(j))
    setProcessed(pair)
    pair
  }

  @inline
  private def wasProcessed(p: (Int, Int)): Boolean =
    val k = if p._1 <= p._2 then PDist.index(p._1, p._2, n) else PDist.index(p._2, p._1, n)
    processed.contains(k)

  @inline
  private def setProcessed(p: (Int, Int)): Unit =
    val k = if p._1 <= p._2 then PDist.index(p._1, p._2, n) else PDist.index(p._2, p._1, n)
    processed += k

  @inline
  private def meanError(p: (Int, Int)): Double =
    meanError(p._1, p._2)

  @inline
  private def meanError(id1: Int, id2: Int): Double =
    (errors(id1) + errors(id2)) / 2
}
