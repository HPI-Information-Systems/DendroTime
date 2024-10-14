package de.hpi.fgis.bloomfilter

import de.hpi.fgis.bloomfilter.hashing.MurmurHash3Generic
import de.hpi.fgis.bloomfilter.util.Unsafe.unsafe

import java.lang.reflect.Field

trait CanGenerate128HashFrom[From] {
  def generateHash(from: From): (Long, Long)
}

object CanGenerate128HashFrom {
  given CanGenerate128HashFrom[Int] with {
    override def generateHash(from: Int): (Long, Long) =
      val hash = MurmurHash3Generic.fmix64(from)
      (hash, hash)
  }

  given CanGenerate128HashFrom[Long] with {
    override def generateHash(from: Long): (Long, Long) =
      val hash = MurmurHash3Generic.fmix64(from)
      (hash, hash)
  }

  given CanGenerate128HashFrom[Array[Byte]] with {
    override def generateHash(from: Array[Byte]): (Long, Long) =
      MurmurHash3Generic.murmurhash3_x64_128(from, 0, from.length, 0)
  }

  // given for Strings
  private case object CanGenerate128HashFromString extends CanGenerate128HashFrom[String] {
    private val valueOffset = unsafe.objectFieldOffset(stringValueField)

    override def generateHash(from: String): (Long, Long) =
      val value = unsafe.getObject(from, valueOffset).asInstanceOf[Array[Char]]
      MurmurHash3Generic.murmurhash3_x64_128(value, 0, value.length * 2, 0)
  }

  private case object CanGenerate128HashFromStringByteArray extends CanGenerate128HashFrom[String] {
    private val valueOffset = unsafe.objectFieldOffset(stringValueField)

    override def generateHash(from: String): (Long, Long) =
      val value = unsafe.getObject(from, valueOffset).asInstanceOf[Array[Byte]]
      MurmurHash3Generic.murmurhash3_x64_128(value, 0, value.length, 0)
  }

  private val stringValueField: Field = classOf[String].getDeclaredField("value")

  given CanGenerate128HashFrom[String] =
    if (stringValueField.getType.getComponentType == java.lang.Byte.TYPE)
      CanGenerate128HashFromStringByteArray
    else
      CanGenerate128HashFromString

}
