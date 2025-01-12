package de.hpi.fgis.bloomfilter.mutable

import de.hpi.fgis.bloomfilter.util.Unsafe.unsafe
import org.apache.commons.math3.util.FastMath.ceil

import java.io.*
import scala.annotation.targetName

@SerialVersionUID(2L)
private[bloomfilter] final class UnsafeBitArray(val numberOfBits: Long) extends Serializable with Equals {
  private val indices = ceil(numberOfBits.toDouble / 64).toLong
  @transient
  private val ptr = unsafe.allocateMemory(8L * indices)
  unsafe.setMemory(ptr, 8L * indices, 0.toByte)
  private var bitCount = 0L

  def get(index: Long): Boolean =
//    require(index >= 0 && index < numberOfBits, s"Index $index is out of bounds for an array of size $numberOfBits")
    (unsafe.getLong(ptr + (index >>> 6) * 8L) & (1L << index)) != 0

  def set(index: Long): Unit =
//    require(index >= 0 && index < numberOfBits, s"Index $index is out of bounds for an array of size $numberOfBits")
    val offset = ptr + (index >>> 6) * 8L
    val long = unsafe.getLong(offset)
    if ((long & (1L << index)) == 0)
      unsafe.putLong(offset, long | (1L << index))
      bitCount += 1

  private def combine(that: UnsafeBitArray, combiner: (Long, Long) => Long): UnsafeBitArray =
    val result = new UnsafeBitArray(this.numberOfBits)
    var index = 0L
    while (index < numberOfBits)
      val thisLong = unsafe.getLong(this.ptr + (index >>> 6) * 8L)
      val thatLong = unsafe.getLong(that.ptr + (index >>> 6) * 8L)
      val longAtIndex = combiner(thisLong, thatLong)
      unsafe.putLong(result.ptr + (index >>> 6) * 8L, longAtIndex)
      result.bitCount += java.lang.Long.bitCount(longAtIndex)
      index += 64
    result

  @targetName("or")
  def |(that: UnsafeBitArray): UnsafeBitArray =
    require(this.numberOfBits == that.numberOfBits, "Bitwise OR works only on arrays with the same number of bits")

    combine(that, _ | _)

  @targetName("and")
  def &(that: UnsafeBitArray): UnsafeBitArray =
    require(this.numberOfBits == that.numberOfBits, "Bitwise AND works only on arrays with the same number of bits")

    combine(that, _ & _)

  def getBitCount: Long = bitCount

  def writeTo(out: OutputStream): Unit =
    val dout = new DataOutputStream(out)
    dout.writeLong(bitCount)
    var index = 0L
    while (index < numberOfBits)
      dout.writeLong(unsafe.getLong(this.ptr + (index >>> 6) * 8L))
      index += 64

  def readFrom(in: InputStream): Unit =
    val din = new DataInputStream(in)
    bitCount = din.readLong()
    var index = 0L
    while (index < numberOfBits)
      unsafe.putLong(this.ptr + (index >>> 6) * 8L, din.readLong())
      index += 64

  def dispose(): Unit = unsafe.freeMemory(ptr)

  @throws(classOf[java.io.ObjectStreamException])
  private def writeReplace: AnyRef = new UnsafeBitArray.SerializedForm(this)

  override def canEqual(that: Any): Boolean = that.isInstanceOf[UnsafeBitArray]

  override def equals(obj: Any): Boolean = obj.asInstanceOf[Matchable] match
    case that: UnsafeBitArray =>
      (this eq that) || (
        this.canEqual(that) && this.hashCode == that.hashCode &&
          this.numberOfBits == that.numberOfBits && compareBits(that)
        )
    case _ => false

  private def compareBits(that: UnsafeBitArray): Boolean =
    var index = 0L
    while (index < numberOfBits)
      if (unsafe.getLong(this.ptr + (index >>> 6) * 8L) != unsafe.getLong(that.ptr + (index >>> 6) * 8L))
        return false
      index += 64
    true

  override def hashCode(): Int =
    var hash = 31 * numberOfBits.##
    var index = 0L
    while (index < numberOfBits)
      hash = 31 * hash + unsafe.getLong(this.ptr + (index >>> 6) * 8L).##
      index += 64
    hash
}

object UnsafeBitArray {

  @SerialVersionUID(1L)
  private class SerializedForm(@transient var unsafeBitArray: UnsafeBitArray) extends Serializable:
    private def writeObject(oos: ObjectOutputStream): Unit =
      oos.defaultWriteObject()
      oos.writeLong(unsafeBitArray.numberOfBits)
      unsafeBitArray.writeTo(oos)

    private def readObject(ois: ObjectInputStream): Unit =
      ois.defaultReadObject()
      val numberOfBits = ois.readLong()
      unsafeBitArray = new UnsafeBitArray(numberOfBits)
      unsafeBitArray.readFrom(ois)

    @throws(classOf[java.io.ObjectStreamException])
    private def readResolve: AnyRef = unsafeBitArray

}
