package de.hpi.fgis.bloomfilter

import de.hpi.fgis.bloomfilter.CanGetDataFrom.canGetDataFromArrayChar
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should

class CanGetDataFromSpec extends AnyFreeSpec with should.Matchers {
  "canGetDataFromArrayChar" in {
    canGetDataFromArrayChar.getByte(Array[Char]('a'), 0) shouldEqual 97.toByte
    canGetDataFromArrayChar.getByte(Array[Char]('a'), 1) shouldEqual 0.toByte

    canGetDataFromArrayChar.getByte(
      Array[Char]('a', 'b'),
      0
    ) shouldEqual 97.toByte
    canGetDataFromArrayChar.getByte(
      Array[Char]('a', 'b'),
      1
    ) shouldEqual 0.toByte
    canGetDataFromArrayChar.getByte(
      Array[Char]('a', 'b'),
      2
    ) shouldEqual 98.toByte
    canGetDataFromArrayChar.getByte(
      Array[Char]('a', 'b'),
      3
    ) shouldEqual 0.toByte

    canGetDataFromArrayChar.getLong(
      Array[Char]('a', 'b', 'c', 'd'),
      0
    ) shouldEqual
      (0.toLong << 56) |
        (('d'.toByte & 0xffL) << 48) |
        ((0 & 0xffL) << 40) |
        (('c'.toByte & 0xffL) << 32) |
        ((0 & 0xffL) << 24) |
        (('b' & 0xffL) << 16) |
        ((0 & 0xffL) << 8) |
        'a' & 0xffL
  }
}
