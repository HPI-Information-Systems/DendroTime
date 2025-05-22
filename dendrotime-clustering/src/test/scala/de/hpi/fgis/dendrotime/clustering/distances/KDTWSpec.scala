package de.hpi.fgis.dendrotime.clustering.distances

import de.hpi.fgis.dendrotime.clustering.TestUtil
import org.scalatest.matchers.*
import org.scalatest.wordspec.AnyWordSpec

class KDTWSpec extends AnyWordSpec with should.Matchers {

  import TestUtil.ImplicitEqualities.given

  "The normalized KDTW distance" when {
    val kdtw = KDTW(gamma = 1.0, epsilon = 1e-20, normalizeInput = true, normalizeDistance = true)

    "given two equal-length arrays" should {
      "throw an IllegalArgumentException for an invalid gamma" in {
        val x = Array(1.0, 2.0, 3.0)
        val y = Array(1.0, 2.0, 3.0)
        assertThrows[IllegalArgumentException] {
          KDTW(gamma = 0.0)(x, y)
        }
      }
      "be 0 for two empty arrays" in {
        val x = Array.empty[Double]
        val y = Array.empty[Double]
        kdtw(x, y) shouldEqual 0.0
      }
      "be 0 for two identical arrays" in {
        val x = Array(1.0, 2.0, 3.0)
        val y = Array(1.0, 2.0, 3.0)
        kdtw(x, y) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)
        kdtw(x, y) shouldEqual 0.192294943
      }
    }
    "given two unequal-length arrays" should {
      "be 0 for an empty array and an array with one element" in {
        val x = Array.empty[Double]
        val y = Array(1.0)
        kdtw(x, y) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114)
        kdtw(x, y) shouldEqual 0.999070123
      }
    }
    "compared to reference impl." when {
      val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
      val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)

      val expected = Map(
        // gamma, epsilon -> result
        (1e-3, 1e-3) -> 1.0,
        (1e-3, 1e-10) -> 1.0,
        (1e-3, 1e-20) -> 1.0,
        (0.125, 1e-3) -> 0.994014033,
        (0.125, 1e-10) -> 0.994048564,
        (0.125, 1e-20) -> 0.994048564,
        (0.9, 1e-3) -> 0.214603412,
        (0.9, 1e-10) -> 0.214856459,
        (0.9, 1e-20) -> 0.214856459,
      )

      for gamma <- Seq(1e-3, 0.125, 0.9) do
        for epsilon <- Seq(1e-3, 1e-10, 1e-20) do
          s"gamma=$gamma, epsilon=$epsilon" should {
            "produce the same results" in {
              val distance = KDTW(gamma=gamma, epsilon=epsilon)(x, y)
              distance shouldEqual expected(gamma -> epsilon)
            }
          }
    }
  }

  "The unnormalized KDTW distance" when {
    val kdtw = KDTW(gamma = 1.0, epsilon = 1e-20, normalizeInput = false, normalizeDistance = true)

    "given two equal-length arrays" should {
      "throw an IllegalArgumentException for an invalid gamma" in {
        val x = Array(1.0, 2.0, 3.0)
        val y = Array(1.0, 2.0, 3.0)
        assertThrows[IllegalArgumentException] {
          KDTW(gamma = 0.0)(x, y)
        }
      }
      "be 0 for two empty arrays" in {
        val x = Array.empty[Double]
        val y = Array.empty[Double]
        kdtw(x, y) shouldEqual 0.0
      }
      "be 0 for two identical arrays" in {
        val x = Array(1.0, 2.0, 3.0)
        val y = Array(1.0, 2.0, 3.0)
        kdtw(x, y) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)
        kdtw(x, y) shouldEqual 1.0
      }
    }
    "given two unequal-length arrays" should {
      "be 0 for an empty array and an array with one element" in {
        val x = Array.empty[Double]
        val y = Array(1.0)
        kdtw(x, y) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114)
        kdtw(x, y) shouldEqual 1.0
      }
    }
  }

  "The KDTW pairwise distance" when {
    val kdtw = KDTW(gamma = 1.0, epsilon = 1e-20, normalizeInput = true, normalizeDistance = true)

    "given two equal-length arrays" should {
      "be empty for two empty arrays" in {
        val x = Array.empty[Array[Double]]
        kdtw.pairwise(x) shouldEqual Array.empty[Array[Double]]
      }
      "be 0 for two identical arrays" in {
        val x = Array(
          Array(1.0, 2.0, 3.0),
          Array(1.0, 2.0, 3.0)
        )
        kdtw.pairwise(x) shouldEqual Array.tabulate(2, 2)((_, _) => 0.0)
      }
      "compute correctly" in {
        val x = Array(
          Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182),
          Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)
        )
        kdtw.pairwise(x) shouldEqual Array(Array(0.0, 0.192294943), Array(0.192294943, 0.0))
      }
    }
    "given two unequal-length arrays" should {
      "be 0 for an empty array and an array with one element" in {
        val x = Array(
          Array.empty[Double],
          Array(1.0)
        )
        kdtw.pairwise(x) shouldEqual Array.tabulate(2, 2)((_, _) => 0.0)
      }
      "compute correctly" in {
        val x = Array(
          Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182),
          Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114)
        )
        kdtw.pairwise(x) shouldEqual Array(Array(0.0, 0.999070123), Array(0.999070123, 0.0))
      }
    }
  }

  "compare to reference for Coffee dataset" in {
    val expectedDistanceMatrix = TestUtil.loadCSVFile("test-data/distance-matrix-coffee-kdtw.csv")
    val coffeeTrainData = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/Coffee/Coffee_TRAIN.ts"))
    val coffeeTestData = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/Coffee/Coffee_TEST.ts"))
    val data = coffeeTrainData ++ coffeeTestData
    val kdtw = KDTW(gamma = 1.0, epsilon = 1e-20, normalizeInput = true, normalizeDistance = true)

    val single01 = kdtw(data(0), data(1))
    single01 shouldEqual expectedDistanceMatrix(0)(1)

    val distanceMatrix = kdtw.pairwise(data)
    distanceMatrix.length shouldEqual expectedDistanceMatrix.length
    distanceMatrix shouldEqual expectedDistanceMatrix
  }
  "compare to reference for PickupGestureWiimoteZ dataset" in {
    val expectedDistanceMatrix = TestUtil.loadCSVFile("test-data/distance-matrix-PGWZ-kdtw.csv")
    val dataTrain = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TRAIN.ts"))
    val dataTest = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TEST.ts"))
    val data = dataTrain ++ dataTest
    val kdtw = KDTW(gamma = 1.0, epsilon = 1e-20, normalizeInput = true, normalizeDistance = true)

    val single01 = kdtw(data(7), data(11))
    single01 shouldEqual expectedDistanceMatrix(7)(11)

    val distanceMatrix = kdtw.pairwise(data)
    distanceMatrix.length shouldEqual expectedDistanceMatrix.length
    distanceMatrix shouldEqual expectedDistanceMatrix
  }

}
