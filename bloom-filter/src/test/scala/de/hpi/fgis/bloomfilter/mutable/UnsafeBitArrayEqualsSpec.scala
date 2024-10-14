package de.hpi.fgis.bloomfilter.mutable

import de.hpi.fgis.bloomfilter.mutable.BloomFilter64
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class UnsafeBitArrayEqualsSpec extends AnyWordSpec with should.Matchers {
  "The UnsafeBitArray" should {
    "be equal to another UnsafeBitArray with the same bits" in {
      for n <- 10 to 1000 by 10 do
        val bits = new UnsafeBitArray(n)
        bits.set(0)
        bits.set(1)
        bits.set(2)
        bits.set(3)
        bits.set(6)
        bits.set(7)
        bits.set(8)
        bits.set(9)
  
        val otherBits = new UnsafeBitArray(n)
        otherBits.set(0)
        otherBits.set(1)
        otherBits.set(2)
        otherBits.set(3)
        otherBits.set(6)
        otherBits.set(7)
        otherBits.set(8)
        otherBits.set(9)
  
        bits.hashCode() shouldEqual otherBits.hashCode()
        bits shouldEqual otherBits
    }
    
    "not be equal to another UnsafeBitArray with different bits" in {
      for n <- 10 to 1000 by 10 do
        val bits = new UnsafeBitArray(n)
        bits.set(0)
        bits.set(1)
        bits.set(2)
        bits.set(3)
        bits.set(6)
        bits.set(7)
        bits.set(8)
        bits.set(9)
  
        val otherBits = new UnsafeBitArray(n)
        otherBits.set(0)
        otherBits.set(1)
        otherBits.set(2)
        otherBits.set(3)
        otherBits.set(4)
        otherBits.set(5)

        bits.hashCode() should not equal otherBits.hashCode()
        bits should not equal otherBits
    }
  }
}
