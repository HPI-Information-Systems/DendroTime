package de.hpi.fgis.dendrotime.io

import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVReader
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class HierarchyCSVReaderSpec extends AnyWordSpec with should.Matchers {

  "HierarchyCSVReader" should {
    "parse a CSV file" in {
      val reader = HierarchyCSVReader()
      val hierarchy = reader.parse(Fixtures.hierarchyFile)
      hierarchy should not be null
      hierarchy.n shouldBe 135
      hierarchy.length shouldBe 134
      hierarchy(hierarchy.length - 1).cardinality shouldBe 135
      for i <- hierarchy.indices do
        hierarchy(i) shouldEqual Fixtures.hierarchy(i)
    }
  }
}
