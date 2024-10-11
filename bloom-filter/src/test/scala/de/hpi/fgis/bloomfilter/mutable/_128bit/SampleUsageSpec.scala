package de.hpi.fgis.bloomfilter.mutable._128bit

import de.hpi.fgis.bloomfilter.mutable._128bit.BloomFilter
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class SampleUsageSpec extends AnyWordSpec with should.Matchers {
  "Create, put and check" in {
    val bloomFilter = BloomFilter[String](1000, 0.01)

    bloomFilter.add("")
    bloomFilter.add("Hello!")
    bloomFilter.add("8f16c986824e40e7885a032ddd29a7d3")

    bloomFilter.mightContain("") shouldBe true
    bloomFilter.mightContain("Hello!") shouldBe true
    bloomFilter.mightContain("8f16c986824e40e7885a032ddd29a7d3") shouldBe true

    bloomFilter.dispose()
  }
}
