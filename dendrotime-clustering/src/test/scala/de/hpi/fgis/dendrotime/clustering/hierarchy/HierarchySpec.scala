package de.hpi.fgis.dendrotime.clustering.hierarchy

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class HierarchySpec extends AnyWordSpec with should.Matchers {

  "The hierarchy factory" should {
    "create an empty hierarchy" in {
      val z = Hierarchy.empty
      z.length shouldEqual 0
      z.n shouldEqual 0
    }
    "create a new hierarchy from an array" in {
      val h = Hierarchy.fromArray(Array(Array(0, 1, 2.0, 2), Array(2, 3, 4.0, 2), Array(4, 5, 4.0, 4)))
      h.length shouldEqual 3
      h.n shouldEqual 4
      h(0) shouldEqual Hierarchy.Node(0, 0, 1, 2.0, 2)
      h.cardinality(2) shouldEqual 4
    }
    "create a new hierarchy using the builder pattern" in {
      val z = Hierarchy.newBuilder(4)
        .add(0, 1, 2.0, 2)
        .add(2, 3, 3.8, 2)
        .add(Hierarchy.Node(-1, 4, 5, 4.0, 4))
        .update(1, Hierarchy.Node(1, 2, 3, 1.8, 2))
        .sort()
        .build()
      z.length shouldEqual 3
      z.n shouldEqual 4
      z(0) shouldEqual Hierarchy.Node(0, 2, 3, 1.8, 2)
      z(1) shouldEqual Hierarchy.Node(1, 0, 1, 2.0, 2)
      z.cId1(2) shouldEqual 4
      z.cId2(2) shouldEqual 5
      z.distance(2) shouldEqual 4.0
      z.cardinality(2) shouldEqual 4
    }
  }
}
