package de.hpi.fgis.bloomfilter

import de.hpi.fgis.bloomfilter.mutable.UnsafeBitArray
import org.apache.commons.math3.util.FastMath.{abs, log1p, rint, copySign}

import java.io.OutputStream
import scala.annotation.targetName


trait BloomFilter[T] extends Serializable with AutoCloseable with Equals {

  def numberOfBits: Long

  def numberOfHashes: Int

  def approximateElementCount: Long

  def expectedFalsePositiveRate: Double

  def add(x: T): Unit

  def union(that: BloomFilter[T]): BloomFilter[T]

  def intersect(that: BloomFilter[T]): BloomFilter[T]

  def mightContain(x: T): Boolean

  def writeTo(out: OutputStream): Unit

  def dispose(): Unit

  override def close(): Unit = dispose()

  @targetName("addOp")
  def +(x: T): Unit = add(x)

  @targetName("unionOp")
  def ++(xs: IterableOnce[T]): Unit = xs.iterator.foreach(add)

  @targetName("mutableAddOp")
  def +=(x: T): this.type =
    add(x)
    this

  @targetName("mutableUnionOp")
  def ++=(xs: IterableOnce[T]): this.type =
    xs.iterator.foreach(add)
    this

  @targetName("and")
  def &(that: BloomFilter[T]): BloomFilter[T] = intersect(that)

  @targetName("or")
  def |(that: BloomFilter[T]): BloomFilter[T] = union(that)

  protected def internalApproximateElementCount(bits: UnsafeBitArray): Long = {
    val fractionOfBitsSet = bits.getBitCount.toDouble / numberOfBits
    val x = -log1p(-fractionOfBitsSet) * numberOfBits / numberOfHashes
    val z = rint(x)
    if (abs(x - z) == 0.5)
      (x + copySign(0.5, x)).toLong
    else
      z.toLong
  }
}

object BloomFilter extends BloomFilterFactory {
  /**
   * Creates a new BloomFilter with the given number of items and the implicitly supplied options.
   * The default options are defined in [[de.hpi.fgis.bloomfilter.BloomFilterOptions$.DEFAULT_OPTIONS]].
   *
   * @param numberOfItems the expected number of elements to be inserted into the BloomFilter
   * @param options the options to be used for the BloomFilter
   * @tparam T the type of the elements to be inserted into the BloomFilter
   * @return a new empty BloomFilter instance
   */
  def apply[T: CanGenerateHashFrom](numberOfItems: Long)(using options: BloomFilterOptions): BloomFilter[T] = options match {
    case BloomFilterOptions(BloomFilterOptions.BFHashSize.BFH64, fpr, BloomFilterOptions.BFType.BloomFilter) =>
      val nb = optimalNumberOfBits(numberOfItems, fpr)
      val nh = optimalNumberOfHashes(numberOfItems, nb)
      new mutable.BloomFilter64[T](nb, nh)
    case BloomFilterOptions(BloomFilterOptions.BFHashSize.BFH128, fpr, BloomFilterOptions.BFType.BloomFilter) =>
      val nb = optimalNumberOfBits(numberOfItems, fpr)
      val nh = optimalNumberOfHashes(numberOfItems, nb)
      new mutable._128bit.BloomFilter128[T](nb, nh)
    case _ =>
      throw new IllegalArgumentException("Unsupported BloomFilterOptions")
  }
}