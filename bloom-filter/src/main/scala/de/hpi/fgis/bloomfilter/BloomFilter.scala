package de.hpi.fgis.bloomfilter

import de.hpi.fgis.bloomfilter.mutable.UnsafeBitArray

import java.io.OutputStream
import scala.annotation.targetName
import scala.math.{abs, log1p, rint}

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
      (x + Math.copySign(0.5, x)).toLong
    else
      z.toLong
  }
}
