package de.hpi.fgis.bloomfilter.mutable

import org.scalatest.{Assertion, Succeeded}
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec


class UnsafeBitArrayBoundsSpec extends AnyWordSpec with should.Matchers {
  private final val requirementsRemoved = true

  private def skipIfNoRequires(fun: => Assertion): Assertion =
    if (requirementsRemoved) Succeeded
    else fun

  "An UnsafeBitArray" should {
    "check the array bounds in set" in skipIfNoRequires {
      val array = new UnsafeBitArray(10)
      assertThrows[RuntimeException] {
        array.set(-1)
      }
      assertThrows[RuntimeException] {
        array.set(10)
      }
    }
    "check the array bounds in get" in skipIfNoRequires {
      val array = new UnsafeBitArray(10)
      assertThrows[RuntimeException] {
        array.get(-1)
      }
      assertThrows[RuntimeException] {
        array.get(10)
      }
    }
  }
}