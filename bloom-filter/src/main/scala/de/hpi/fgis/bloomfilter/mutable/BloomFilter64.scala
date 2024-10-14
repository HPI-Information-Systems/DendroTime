package de.hpi.fgis.bloomfilter.mutable

import de.hpi.fgis.bloomfilter.{BloomFilter, CanGenerateHashFrom}

import java.io.{DataInputStream, DataOutputStream, InputStream, OutputStream}
import scala.math.*

@SerialVersionUID(2L)
class BloomFilter64[T] private(val numberOfBits: Long, val numberOfHashes: Int, private val bits: UnsafeBitArray)
                              (using canGenerateHash: CanGenerateHashFrom[T]) extends BloomFilter[T] {

  def this(numberOfBits: Long, numberOfHashes: Int)(using CanGenerateHashFrom[T]) =
    this(numberOfBits, numberOfHashes, new UnsafeBitArray(numberOfBits))

  override val expectedFalsePositiveRate: Double =
    math.pow(bits.getBitCount.toDouble / numberOfBits, numberOfHashes.toDouble)

  override def approximateElementCount: Long = internalApproximateElementCount(bits)

  override def add(x: T): Unit =
    val hash = canGenerateHash.generateHash(x)
    val hash1 = hash >>> 32
    val hash2 = (hash << 32) >> 32

    var i = 0
    while (i < numberOfHashes)
      val computedHash = hash1 + i * hash2
      bits.set((computedHash & Long.MaxValue) % numberOfBits)
      i += 1

  override def union(that: BloomFilter[T]): BloomFilter[T] = that match {
    case that64: BloomFilter64[T] =>
      require(
        this.numberOfBits == that.numberOfBits && this.numberOfHashes == that.numberOfHashes,
        s"Union works only on BloomFilters with the same number of hashes and of bits"
      )
      new BloomFilter64[T](
        this.numberOfBits,
        this.numberOfHashes,
        this.bits | that64.bits
      )
    case _ =>
      throw new IllegalArgumentException("Union works only on BloomFilters of the same type")
  }

  override def intersect(that: BloomFilter[T]): BloomFilter[T] = that match {
    case that64: BloomFilter64[T] =>
      require(
        this.numberOfBits == that.numberOfBits && this.numberOfHashes == that.numberOfHashes,
        s"Intersect works only on BloomFilters with the same number of hashes and of bits"
      )
      new BloomFilter64[T](
        this.numberOfBits,
        this.numberOfHashes,
        this.bits & that64.bits
      )
    case _ =>
      throw new IllegalArgumentException("Union works only on BloomFilters of the same type")
  }

  override def mightContain(x: T): Boolean =
    val hash = canGenerateHash.generateHash(x)
    val hash1 = hash >>> 32
    val hash2 = (hash << 32) >> 32
    var i = 0
    while (i < numberOfHashes)
      val computedHash = hash1 + i * hash2
      if (!bits.get((computedHash & Long.MaxValue) % numberOfBits))
        return false
      i += 1
    true

  override def writeTo(out: OutputStream): Unit =
    val dout = new DataOutputStream(out)
    dout.writeLong(numberOfBits)
    dout.writeInt(numberOfHashes)
    bits.writeTo(out)

  override def dispose(): Unit = bits.dispose()

  override def toString: String =
    s"BloomFilter64(numberOfBits=$numberOfBits, numberOfHashes=$numberOfHashes, approximateElementCount=$approximateElementCount)"

  override def canEqual(that: Any): Boolean = that.isInstanceOf[BloomFilter64[T]]
  
  override def equals(that: Any): Boolean = that match {
    case otherFilter: BloomFilter64[T] =>
      (this eq otherFilter) || (
        otherFilter.canEqual(this) &&
        this.hashCode == otherFilter.hashCode &&
        this.numberOfBits == otherFilter.numberOfBits &&
        this.numberOfHashes == otherFilter.numberOfHashes &&
        this.bits == otherFilter.bits
      )
    case _ => false
  }

  override def hashCode(): Int = 31 * (31 * numberOfBits.## + numberOfHashes.##) + bits.##
}

object BloomFilter64 {

  def apply[T](numberOfItems: Long, falsePositiveRate: Double)(using CanGenerateHashFrom[T]): BloomFilter64[T] =
    val nb = optimalNumberOfBits(numberOfItems, falsePositiveRate)
    val nh = optimalNumberOfHashes(numberOfItems, nb)
    new BloomFilter64[T](nb, nh)

  def optimalNumberOfBits(numberOfItems: Long, falsePositiveRate: Double): Long =
    math.ceil(-1 * numberOfItems * math.log(falsePositiveRate) / math.log(2) / math.log(2)).toLong

  def optimalNumberOfHashes(numberOfItems: Long, numberOfBits: Long): Int =
    math.ceil(numberOfBits / numberOfItems * math.log(2)).toInt

  def readFrom[T](in: InputStream)(using CanGenerateHashFrom[T]): BloomFilter64[T] =
    val din = new DataInputStream(in)
    val numberOfBits = din.readLong()
    val numberOfHashes = din.readInt()
    val bits = new UnsafeBitArray(numberOfBits)
    bits.readFrom(in)
    new BloomFilter64[T](numberOfBits, numberOfHashes, bits)
}
