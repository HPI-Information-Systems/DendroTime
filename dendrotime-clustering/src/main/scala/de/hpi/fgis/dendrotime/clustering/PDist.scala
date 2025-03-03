package de.hpi.fgis.dendrotime.clustering

import org.apache.commons.math3.util.FastMath

import scala.collection.mutable

/**
 * Pairwise distances as a vector of length n*(n-1)/2.
 * 
 * Stores the distances of the lower triangle in column-wise fashion:
 * d(0,1), d(0,2), ..., d(1,2), d(1,3), ..., d(2,3), ...
 *
 * The pairwise distance between observations i and j is at index ((i-1)*(n-i/2)+j-i) for i≤j.
 */
trait PDist extends Iterable[Double] {
  def apply(i: Int, j: Int): Double

  /** Number of pairwise distances in this vector. */
  def length: Int

  /** Number of original observations that were compared in pairs. */
  def n: Int

  /** Internal flat vector of distances. Use on your own risk! */
  def distances: Array[Double]

  /** Convert to mutable object that supports update(). */
  def mutable: MutablePDist

  /** Create a mutable copy of this object. */
  def mutableCopy: MutablePDist

  /** Convert to immutable object. */
  def immutable: PDist = this

  /** Extract pairwise distance vector of a subset of objects. */
  def subPDistOf(indices: scala.collection.Seq[Int]): PDist = PDist.subPDistOf(this, indices)

  /** Convert to a quadratic distance matrix. */
  def matrixView: IndexedSeq[IndexedSeq[Double]] = new PDist.PDistMatrixView(this)

  override def knownSize: Int = length
}

trait MutablePDist extends PDist with mutable.Iterable[Double] {
  def update(i: Int, j: Int, value: Double): Unit

  override def mutable: MutablePDist = this
}

object PDist {
  /**
   * Given the observations i and j, return the index of the pairwise distance in the compact vector.
   * i and j are 0-based; i must be less than j.
   *
   * index = ((2*n-1)*i - i*i)/2 + j - i + 1
   *
   * @param i index of the first observation
   * @param j index of the second observation
   * @param n number of observations
   * @return index of the pairwise distance in the compact vector
   */
  @inline
  def index(i: Int, j: Int, n: Int): Int = (- i*i + (2*n-3)*i + 2*j)/2 - 1

  /**
   * Create a new empty pairwise distance vector of size n*(n-1)/2.
   *
   * @param n number of observations
   * @return empty pairwise distance vector of size n*(n-1)/2
   */
  def empty(n: Int): PDist = PDistImpl(Array.fill(n * (n - 1) / 2)(Double.PositiveInfinity), n)

  /**
   * Create a compact pairwise distance vector from a quadratic distance matrix verifying the size of the matrix.
   *
   * @param dists pairwise distance matrix of size n x n
   * @param n number of observations
   * @return pairwise distance vector of size n*(n-1)/2
   */
  def apply(dists: Array[Array[Double]], n: Int): PDist =
    if dists.length != n || dists.exists(_.length != n) then
      throw new IllegalArgumentException("Distance matrix must be square.")
    // convert quadratic pairwise distance matrix into compact form
    PDistImpl(Array.from(for i <- 0 until n; j <- i + 1 until n yield dists(i)(j)), n)

  /**
   * Create a compact pairwise distance vector from a quadratic distance matrix.
   *
   * @param dists pairwise distance matrix of size n x n
   * @return pairwise distance vector of size n*(n-1)/2
   */
  def apply(dists: Array[Array[Double]]): PDist =
    apply(dists, dists.length)

  /**
   * Wrap an existing compact pairwise distance vector of size n*(n-1)/2 for n objects.
   *
   * @param distances list of n*(n-1)/2 pairwise distances
   * @return pairwise distance vector of size n*(n-1)/2
   */
  def unsafeWrapArray(distances: Array[Double]): PDist =
    PDistImpl(distances, FastMath.ceil(FastMath.sqrt(2 * distances.length)).toInt)

  /** Extract a pairwise distance vector for a subset of the objects in `pdist`.
   * 
   * @param pdist source pairwise distance vector
   * @param indices indices of the objects to extract (must be between 0 and pdist.n-1)
   * @return pairwise distance vector for the subset of objects in order of `indices`
   * */
  def subPDistOf(pdist: PDist, indices: scala.collection.Seq[Int]): PDist = {
    if indices.min < 0 || indices.max >= pdist.n then
      throw new IllegalArgumentException(s"Indices must be included in PDist range: 0 ... ${pdist.n-1}.")
    val n = indices.length
    val subDistances = Array.fill(n * (n - 1) / 2)(Double.PositiveInfinity)
    for i <- 0 until n; j <- i + 1 until n do
      subDistances(PDist.index(i, j, n)) = pdist(indices(i), indices(j))
    PDistImpl(subDistances, n)
  }

  /**
   * Create a compact pairwise distance vector from a sequence of distances.
   * 
   * @param n number of observations
   * @param distances list of n*(n-1)/2 pairwise distances
   * @return pairwise distance vector of size n*(n-1)/2
   */
  private[clustering] def apply(n: Int)(distances: Double*): PDist = {
    if distances.length != n * (n - 1) / 2 then
      throw new IllegalArgumentException("Number of distances does not match n.")
    PDistImpl(distances.toArray, n)
  }

  private case class PDistImpl private[PDist](distances: Array[Double], n: Int) extends PDist with MutablePDist {
    def apply(i: Int, j: Int): Double = {
      if (i == j) 0.0
      else if (i < j) distances(PDist.index(i, j, n))
      else distances(PDist.index(j, i, n))
    }

    def length: Int = distances.length

    def update(i: Int, j: Int, value: Double): Unit = {
      if (i == j) throw new IllegalArgumentException("Cannot set self-distance, it is always 0.")
      else if (i < j) distances(PDist.index(i, j, n)) = value
      else distances(PDist.index(j, i, n)) = value
    }

    override def mutableCopy: MutablePDist = copy(distances = distances.clone())

    override def toString: String = s"""Pairwise distances of $n observations:\n${distances.mkString(", ")}"""
    
    override def iterator: Iterator[Double] = distances.iterator
  }

  /** 2D view into the PDist vector. Allocates a row-view object on each access. */
  private final class PDistMatrixView private[PDist](pdist: PDist) extends IndexedSeq[IndexedSeq[Double]] {

    override def apply(i: Int): IndexedSeq[Double] = new IndexedSeq[Double] {
      override def apply(j: Int): Double = {
        if (i == j) 0.0
        else if (i < j) pdist(i, j)
        else pdist(j, i)
      }

      override def length: Int = pdist.n
    }

    override def length: Int = pdist.n
  }
}
