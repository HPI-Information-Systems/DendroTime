package de.hpi.fgis.dendrotime.io

trait AnyValConverter[T <: AnyVal] {
  def fromString(s: String): T
}

object AnyValConverter {

  def fromString[T <: AnyVal](s: String)(using c: AnyValConverter[T]): T = c.fromString(s)

  given AnyValConverter[Double] with
    def fromString(s: String): Double = s.toDouble

  given AnyValConverter[Float] with
    def fromString(s: String): Float = s.toFloat

  given AnyValConverter[Long] with
    def fromString(s: String): Long = s.toLong

  given AnyValConverter[Int] with
    def fromString(s: String): Int = s.toInt

  given AnyValConverter[Short] with
    def fromString(s: String): Short = s.toShort

  given AnyValConverter[Byte] with
    def fromString(s: String): Byte = s.toByte

  given AnyValConverter[Boolean] with
    def fromString(s: String): Boolean = s.toBoolean

  given AnyValConverter[Char] with
    def fromString(s: String): Char = s.head
}
