package de.hpi.fgis.dendrotime.clustering.hierarchy

import de.hpi.fgis.dendrotime.TestUtil
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class CutTreeSpec extends AnyWordSpec with should.Matchers {
  // expected values were generated using
  // scipy.cluster.hierarchy.cut_tree(hierarchy, n_clusters=n_clusters, height=None)

  "The CutTree algorithm" should {
    "compute the labels correctly" when {
      val n = 15
      val z = Array(
        Array(9.0, 12.0, 1.74862915, 2.0),
        Array(6.0, 10.0, 1.95268802, 2.0),
        Array(0.0, 11.0, 1.97721192, 2.0),
        Array(7.0, 8.0, 2.09256529, 2.0),
        Array(2.0, 16.0, 2.16483412, 3.0),
        Array(5.0, 14.0, 2.32699711, 2.0),
        Array(4.0, 13.0, 2.45833117, 2.0),
        Array(3.0, 15.0, 2.62683079, 3.0),
        Array(1.0, 21.0, 2.66870858, 3.0),
        Array(20.0, 23.0, 3.35108907, 5.0),
        Array(17.0, 18.0, 3.3769862, 4.0),
        Array(19.0, 22.0, 3.83899084, 6.0),
        Array(24.0, 25.0, 4.64328003, 9.0),
        Array(26.0, 27.0, 5.40720586, 15.0),
      )
      val hierarchy = Hierarchy.fromArray(z)
      "1 cluster" in {
        val n_clusters = 1
        val expectedLabels = Array.fill(n)(0)
        val labels = CutTree(hierarchy, n_clusters)
        labels shouldEqual expectedLabels
      }
      "2 clusters" in {
        val n_clusters = 2
        val expectedLabels = Array(0, 0, 1, 1, 0, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0)
        val labels = CutTree(hierarchy, n_clusters)
        labels shouldEqual expectedLabels
      }
      "4 clusters" in {
        val n_clusters = 4
        val expectedLabels = Array(0, 1, 2, 3, 1, 1, 2, 0, 0, 3, 2, 0, 3, 1, 1)
        val labels = CutTree(hierarchy, n_clusters)
        labels shouldEqual expectedLabels
      }
      "10 clusters" in {
        val n_clusters = 10
        val expectedLabels = Array(0, 1, 2, 3, 4, 5, 2, 6, 6, 7, 2, 0, 7, 8, 9)
        val labels = CutTree(hierarchy, n_clusters)
        labels shouldEqual expectedLabels
      }
      "1,2,4,10 clusters" in {
        val n_clusters = Array(1,2,4,10)
        val expectedLabels = Array(
          Array.fill(n)(0),
          Array(0, 0, 1, 1, 0, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0),
          Array(0, 1, 2, 3, 1, 1, 2, 0, 0, 3, 2, 0, 3, 1, 1),
          Array(0, 1, 2, 3, 4, 5, 2, 6, 6, 7, 2, 0, 7, 8, 9)
        )
        val labels = CutTree(hierarchy, n_clusters)
        labels shouldEqual expectedLabels
      }
    }
    "compare to reference for PGWZ dataset" when {
      for metric <- Seq("sbd", "msm") do
        for linkage <- Seq("single", "complete", "average", "ward") do
          s"using $metric metric and $linkage linkage" in {
            val hierarchy = TestUtil.loadHierarchy(s"test-data/ground-truth/PickupGestureWiimoteZ/hierarchy-$metric-$linkage.csv")
            val expectedLabels = TestUtil.loadCSVFile(s"test-data/ground-truth/PickupGestureWiimoteZ/labels-$metric-$linkage.csv")
              .map(_.map(_.toInt))
            val n_clusters = Array.tabulate(hierarchy.n-1)(i => i+1)
            val predLabels = CutTree(hierarchy, n_clusters)
            predLabels shouldEqual expectedLabels
          }
    }
  }
}
