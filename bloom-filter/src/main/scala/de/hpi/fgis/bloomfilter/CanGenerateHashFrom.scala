package de.hpi.fgis.bloomfilter

import de.hpi.fgis.bloomfilter.hashing.MurmurHash3Generic
import de.hpi.fgis.bloomfilter.util.Unsafe.unsafe

import java.lang.reflect.Field

trait CanGenerateHashFrom[From] {
  def generateHash(from: From): Long
}

object CanGenerateHashFrom {
  given CanGenerateHashFrom[Int] with {
    override def generateHash(from: Int): Long = MurmurHash3Generic.fmix64(from)
  }

  given CanGenerateHashFrom[Long] with {
    override def generateHash(from: Long): Long = MurmurHash3Generic.fmix64(from)
  }

  given CanGenerateHashFrom[Array[Byte]] with {
    override def generateHash(from: Array[Byte]): Long =
      MurmurHash3Generic.murmurhash3_x64_64(from, 0, from.length, 0)
  }

  // given for Strings
  private case object CanGenerateHashFromString extends CanGenerateHashFrom[String] {
    private val valueOffset = unsafe.objectFieldOffset(stringValueField)

    override def generateHash(from: String): Long =
      val value = unsafe.getObject(from, valueOffset).asInstanceOf[Array[Char]]
      MurmurHash3Generic.murmurhash3_x64_64(value, 0, value.length * 2, 0)
  }

  private case object CanGenerateHashFromStringByteArray extends CanGenerateHashFrom[String] {
    private val valueOffset = unsafe.objectFieldOffset(stringValueField)

    override def generateHash(from: String): Long =
      val value = unsafe.getObject(from, valueOffset).asInstanceOf[Array[Byte]]
      MurmurHash3Generic.murmurhash3_x64_64(value, 0, value.length, 0)
  }

  private val stringValueField: Field = classOf[String].getDeclaredField("value")

  given CanGenerateHashFrom[String] =
    if (stringValueField.getType.getComponentType == java.lang.Byte.TYPE)
      CanGenerateHashFromStringByteArray
    else
      CanGenerateHashFromString
}
