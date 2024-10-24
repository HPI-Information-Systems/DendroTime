package de.hpi.fgis.dendrotime.clustering.hierarchy

import de.hpi.fgis.dendrotime.TestUtil
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage.SingleLinkage
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class MstSpec extends AnyWordSpec with should.Matchers {
  // expected values were generated using
  // scipy.cluster.hierarchy.linkage(distances, method="single", metric="something")

  "The hierarchy factory" should {
    "call the MST algorithm for single linkage" in {
      val n = 4
      val distances = PDist(n)(1.0, 3.0, 4.0, 2.0, 5.0, 3.0)
      val h = computeHierarchy(distances, SingleLinkage)
      h.size shouldEqual n - 1
      h.toList shouldEqual List(
        Hierarchy.Node(0, 0, 1, 1.0, 2),
        Hierarchy.Node(1, 2, 4, 2.0, 3),
        Hierarchy.Node(2, 3, 5, 3.0, 4)
      )
    }
  }
  "The MST algorithm" should {
    "compute the hierarchy for 4 TS correctly" in {
      val n = 4
      val distances = PDist(n)(1.0, 3.0, 4.0, 2.0, 5.0, 3.0)
      val h = singleLinkageHierarchyMST(distances, adjustLabels = true)
      h.size shouldEqual n - 1
      h.toList shouldEqual List(
        Hierarchy.Node(0, 0, 1, 1.0, 2),
        Hierarchy.Node(1, 2, 4, 2.0, 3),
        Hierarchy.Node(2, 3, 5, 3.0, 4)
      )
    }
    "compute the hierarchy for 5 TS correctly" in {
      val n = 5
      val distances = PDist(n)(
        0.2277884, 0.91302758, 0.88986393, 0.02212848, 0.04960794,
        0.56610106, 0.00637809, 0.91118529, 0.73390853, 0.91253817
      )

      val h = singleLinkageHierarchyMST(distances, adjustLabels = true)
      h.size shouldEqual n - 1
      h.toList shouldEqual List(
        Hierarchy.Node(0, 1, 4, 0.00637809, 2),
        Hierarchy.Node(1, 0, 5, 0.02212848, 3),
        Hierarchy.Node(2, 2, 6, 0.04960794, 4),
        Hierarchy.Node(3, 3, 7, 0.56610106, 5)
      )
    }
    "compare to reference for PGWZ dataset" in {
      val pairwiseDistances = TestUtil.loadCSVFile(TestUtil.findResource("test-data/distance-matrix-PGWZ-sbd.csv"))
      val expectedHierarchy = TestUtil.loadHierarchy("test-data/ground-truth/PickupGestureWiimoteZ/hierarchy-sbd-single.csv")
      val distances = PDist.apply(pairwiseDistances)
      val h = singleLinkageHierarchyMST(distances, adjustLabels = true)
      h.size shouldEqual expectedHierarchy.size

      import TestUtil.ImplicitEqualities.given
      h shouldEqual expectedHierarchy
    }
  }
}
