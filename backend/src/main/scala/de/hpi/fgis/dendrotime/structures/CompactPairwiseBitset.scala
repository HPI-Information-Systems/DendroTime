package de.hpi.fgis.dendrotime.structures

import de.hpi.fgis.dendrotime.clustering.PDist

import scala.collection.mutable


object CompactPairwiseBitset {
  /**
   * Create a new empty bitset of size n*(n-1)/2.
   *
   * @param n number of observations
   * @return empty bitset of size n*(n-1)/2
   */
  def ofDim(n: Int): CompactPairwiseBitset = {
    val m = n * (n - 1) / 2
    new CompactPairwiseBitset(new mutable.BitSet(m), n, m)
  }

  /**
   * Create a compact pairwise bitset from a list of index pairs.
   *
   * @param n       number of observations
   * @param indices pairwise distance matrix of size n x n
   * @return pairwise distance vector of size n*(n-1)/2
   */
  def apply(n: Int, indices: IterableOnce[(Int, Int)]): CompactPairwiseBitset = {
    val b = ofDim(n)
    b.addAll(indices)
  }
}

/**
 * Bitset for index pairs with a fixed length of n*(n-1)/2. The self-join (indices i=j) is not stored.
 *
 * This representation is compatible with PDist and uses the same indexing technique, and, thus, can
 * be used as a bitmask on the pairwise distance vector.
 *
 * The bit for i and j is at index k = ((i-1)*(n-i/2)+j-i) for i≤j.
 */
case class CompactPairwiseBitset private(buffer: mutable.BitSet, n: Int, m: Int)
  extends mutable.AbstractSet[(Int, Int)] {

  private var currentSize: Int = 0

  def contains(i: Int, j: Int): Boolean =
    if (i == j) false
    else if (i < j) buffer(PDist.index(i, j, n))
    else buffer(PDist.index(j, i, n))

  def apply(i: Int, j: Int): Boolean = contains(i, j)

  def add(i: Int, j: Int): Boolean = contains(i, j) || {
    addOne(i, j)
    true
  }

  def remove(i: Int, j: Int): Boolean = {
    val res = contains(i, j)
    subtractOne(i, j)
    res
  }

  def addOne(i: Int, j: Int): this.type = {
    if (i == j) throw new IllegalArgumentException("Cannot set self-distance, it is always 0.")
    else if (i < j) buffer.add(PDist.index(i, j, n))
    else buffer.add(PDist.index(j, i, n))
    currentSize += 1
    this
  }

  def subtractOne(i: Int, j: Int): this.type = {
    if (i == j) throw new IllegalArgumentException("Cannot set self-distance, it is always 0.")
    else if (i < j) buffer.subtractOne(PDist.index(i, j, n))
    else buffer.subtractOne(PDist.index(j, i, n))
    currentSize -= 1
    this
  }

  def indices: Iterator[(Int, Int)] = new Iterator[(Int, Int)] {
    private var _i = 0
    private var _j = 1

    override def hasNext: Boolean = _i < n - 1 && _j < n

    override def next(): (Int, Int) = {
      val res = (_i, _j)
      _j += 1
      if _j == n then
        _i += 1
        _j = _i + 1
      res
    }
  }

  /** Give a hint on the maximum number of observations (n) to expect to optimize the bit representation!
   *  This internally calculates the expected pairwise indices as new size m = n * (n - 1) / 2.
   *
   * @param size the expected number of observations (n)
   **/
  override def sizeHint(size: Int): Unit = buffer.sizeHint(size * (size - 1) / 2)

  override def knownSize: Int = currentSize

  override def size: Int = currentSize

  override def clear(): Unit = buffer.clear()

  override def addOne(elem: (Int, Int)): this.type = addOne(elem._1, elem._2)

  override def contains(elem: (Int, Int)): Boolean = contains(elem._1, elem._2)

  override def subtractOne(elem: (Int, Int)): this.type = subtractOne(elem._1, elem._2)

  override def toString: String = s"""Bitmask for pairwise indices of $n observations:\n${buffer.mkString(", ")}"""

  override def iterator: Iterator[(Int, Int)] = indices.withFilter(contains)
}
