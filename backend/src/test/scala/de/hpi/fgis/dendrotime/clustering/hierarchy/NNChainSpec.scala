package de.hpi.fgis.dendrotime.clustering.hierarchy

import de.hpi.fgis.dendrotime.TestUtil
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.Linkage.WardLinkage
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class NNChainSpec extends AnyWordSpec with should.Matchers {
  // expected values were generated using
  // scipy.cluster.hierarchy.linkage(distances, method=linkage, metric="something")

  import TestUtil.ImplicitEqualities.given

  "The hierarchy factory" should {
    "call the NNchain algorithm for non-single linkage" in {
      val n = 4
      val distances = PDist(n)(1.0, 3.0, 4.0, 2.0, 5.0, 3.0)
      val h = computeHierarchy(distances, Linkage.WeightedLinkage)
      h.size shouldEqual n - 1
      h.toList shouldEqual List(
        Hierarchy.Node(0, 0, 1, 1.0, 2),
        Hierarchy.Node(1, 2, 4, 2.5, 3),
        Hierarchy.Node(2, 3, 5, 3.75, 4)
      )
    }
  }

  "The NNChain algorithm" should {
    "compute the hierarchy for 4 TS correctly" when {
      val n = 4
      val distances = PDist(n)(1.0, 3.0, 4.0, 2.0, 5.0, 3.0)
      "using SingleLinkage" in {
        val h = NNChain(distances, Linkage.SingleLinkage, adjustLabels = true)
        h.size shouldEqual n - 1
        h.toList shouldEqual List(
          Hierarchy.Node(0, 0, 1, 1.0, 2),
          Hierarchy.Node(1, 2, 4, 2.0, 3),
          Hierarchy.Node(2, 3, 5, 3.0, 4)
        )
      }
      "using CentroidLinkage" in {
        val h = NNChain(distances, Linkage.CentroidLinkage, adjustLabels = true)
        h.size shouldEqual n - 1
        h.toList shouldEqual List(
          Hierarchy.Node(0, 0, 1, 1.0, 2),
          Hierarchy.Node(1, 2, 4, 2.5, 3),
          Hierarchy.Node(2, 3, 5, 3.8873012632302006, 4)
        )
      }
      "using MedianLinkage" in {
        val h = NNChain(distances, Linkage.MedianLinkage, adjustLabels = true)
        h.size shouldEqual n - 1
        h.toList shouldEqual List(
          Hierarchy.Node(0, 0, 1, 1.0, 2),
          Hierarchy.Node(1, 2, 4, 2.5, 3),
          Hierarchy.Node(2, 3, 5, 3.61420807370024, 4)
        )
      }
      "using WeightedLinkage" in {
        val h = NNChain(distances, Linkage.WeightedLinkage, adjustLabels = true)
        h.size shouldEqual n - 1
        h.toList shouldEqual List(
          Hierarchy.Node(0, 0, 1, 1.0, 2),
          Hierarchy.Node(1, 2, 4, 2.5, 3),
          Hierarchy.Node(2, 3, 5, 3.75, 4)
        )
      }
    }
    "compute the hierarchy for 5 TS correctly using CompleteLinkage" in {
      val n = 5
      val distances = PDist(n)(
        0.2277884, 0.91302758, 0.88986393, 0.02212848, 0.04960794,
        0.56610106, 0.00637809, 0.91118529, 0.73390853, 0.91253817
      )

      val h = NNChain(distances, Linkage.CompleteLinkage, adjustLabels = true)
      h.size shouldEqual n-1
      h.toList shouldEqual List(
        Hierarchy.Node(0, 1, 4, 0.00637809, 2),
        Hierarchy.Node(1, 0, 5, 0.2277884, 3),
        Hierarchy.Node(2, 2, 3, 0.91118529, 2),
        Hierarchy.Node(3, 6, 7, 0.91302758, 5)
      )
    }
    "compute the hierarchy for 6 TS correctly using AverageLinkage" in {
      val n = 6
      val distances = PDist(n)(
        0.77395605, 0.43887844, 0.85859792, 0.69736803, 0.09417735, 0.97562235,
        0.7611397,  0.78606431, 0.12811363, 0.45038594, 0.37079802, 0.92676499,
        0.64386512, 0.82276161, 0.4434142
      )

      val h = NNChain(distances, Linkage.AverageLinkage, adjustLabels = true)
      h.size shouldEqual n-1
      h.toList shouldEqual List(
        Hierarchy.Node(0, 0, 5, 0.09417735, 2),
        Hierarchy.Node(1, 2, 4, 0.37079802, 2),
        Hierarchy.Node(2, 1, 6, 0.45103484, 3),
        Hierarchy.Node(3, 3, 7, 0.54712553, 3),
        Hierarchy.Node(4, 8, 9, 0.7456235055555555, 6)
      )
    }
    "compute the hierarchy for 7 TS correctly using WardLinkage" in {
      val n = 7
      val distances = PDist(n)(
        0.77395605, 0.43887844, 0.85859792, 0.69736803, 0.09417735, 0.97562235,
        0.7611397,  0.78606431, 0.12811363, 0.45038594, 0.37079802, 0.92676499,
        0.64386512, 0.82276161, 0.4434142,  0.22723872, 0.55458479, 0.06381726,
        0.82763117, 0.6316644,  0.75808774
      )

      val h = NNChain(distances, Linkage.WardLinkage, adjustLabels = true)
      h.size shouldEqual n-1
      h.toList shouldEqual List(
        Hierarchy.Node(0, 3, 6, 0.06381726, 2),
        Hierarchy.Node(1, 0, 5, 0.09417735, 2),
        Hierarchy.Node(2, 1, 4, 0.12811363, 2),
        Hierarchy.Node(3, 2, 8, 0.7594367497736352, 3),
        Hierarchy.Node(4, 7, 9, 0.7699155396277603, 4),
        Hierarchy.Node(5, 10, 11, 1.1342381287202643, 7)
      )
    }
    "compare to reference for PGWZ dataset" when {
      val pairwiseDistances = TestUtil.loadCSVFile(TestUtil.findResource("test-data/distance-matrix-PGWZ-sbd.csv"))
      val distances = PDist.apply(pairwiseDistances)

      for linkage <- Seq("single", "complete", "average", "ward") do
        s"using single $linkage" in {
          val expectedHierarchy = TestUtil.loadHierarchy(s"test-data/ground-truth/PickupGestureWiimoteZ/hierarchy-sbd-$linkage.csv")
          val h = NNChain(distances, Linkage(linkage), adjustLabels = true)
          h.size shouldEqual expectedHierarchy.size
          h shouldEqual expectedHierarchy
        }
    }
  }
}
