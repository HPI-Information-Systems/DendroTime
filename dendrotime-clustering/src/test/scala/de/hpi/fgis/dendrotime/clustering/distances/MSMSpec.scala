package de.hpi.fgis.dendrotime.clustering.distances

import de.hpi.fgis.dendrotime.clustering.TestUtil
import org.scalatest.matchers.*
import org.scalatest.wordspec.AnyWordSpec

class MSMSpec extends AnyWordSpec with should.Matchers {

  import TestUtil.ImplicitEqualities.given

  "The MSM distance" when {
    val msm = MSM(c = 1.0, itakuraMaxSlope = Double.NaN)

    "given two equal-length arrays" should {
      "throw an IllegalArgumentException if both itakura_max_slope and window are specified" in {
        val x = Array(1.0, 2.0, 3.0)
        val y = Array(1.0, 2.0, 3.0)
        assertThrows[IllegalArgumentException] {
          MSM(itakuraMaxSlope = 0.5, window = 0.5)(x, y)
        }
      }
      "throw an IllegalArgumentException for an invalid itakura_max_slope" in {
        val x = Array(1.0, 2.0, 3.0)
        val y = Array(1.0, 2.0, 3.0)
        assertThrows[IllegalArgumentException] {
          MSM(itakuraMaxSlope = 1.5)(x, y)
        }
      }
      "throw an IllegalArgumentException for an invalid window" in {
        val x = Array(1.0, 2.0, 3.0)
        val y = Array(1.0, 2.0, 3.0)
        assertThrows[IllegalArgumentException] {
          MSM(window = 1.5)(x, y)
        }
      }
      "be 0 for two empty arrays" in {
        val x = Array.empty[Double]
        val y = Array.empty[Double]
        msm(x, y) shouldEqual 0.0
      }
      "be 0 for two identical arrays" in {
        val x = Array(1.0, 2.0, 3.0)
        val y = Array(1.0, 2.0, 3.0)
        msm(x, y) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)
        msm(x, y) shouldEqual 1529.0
      }
    }
    "given two unequal-length arrays" should {
      "be 0 for an empty array and an array with one element" in {
        val x = Array.empty[Double]
        val y = Array(1.0)
        msm(x, y) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114)
        MSM(c = 0.5, window = Double.NaN, itakuraMaxSlope = Double.NaN)(x, y) shouldEqual 2130.0
      }
    }
    "compared to reference impl." when {
      val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
      val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)

      val expected: Map[(Double, Double, Double), Double] = Map(
        // c, window, itakuraMaxSlope -> expected distance
        // Double.NaN is represented as -1 because Double.NaN != Double.NaN
        (0.0, -1.0, -1.0) -> 1515.0,
        (0.0, 0.5, -1.0) -> 1515.0,
        (0.0, 0.8, -1.0) -> 1515.0,
        (0.0, -1.0, 0.5) -> 1515.0,
        (0.0, 0.5, 0.5) -> 1515.0,
        (0.0, 0.8, 0.5) -> 1515.0,
        (0.0, -1.0, 0.8) -> 1515.0,
        (0.0, 0.5, 0.8) -> 1515.0,
        (0.0, 0.8, 0.8) -> 1515.0,
        (0.2, -1.0, -1.0) -> 1517.8,
        (0.2, 0.5, -1.0) -> 1517.8,
        (0.2, 0.8, -1.0) -> 1517.8,
        (0.2, -1.0, 0.5) -> 1517.8,
        (0.2, 0.5, 0.5) -> 1517.8,
        (0.2, 0.8, 0.5) -> 1517.8,
        (0.2, -1.0, 0.8) -> 1517.8,
        (0.2, 0.5, 0.8) -> 1517.8,
        (0.2, 0.8, 0.8) -> 1517.8,
        (3.0, -1.0, -1.0) -> 1557.0,
        (3.0, 0.5, -1.0) -> 1557.0,
        (3.0, 0.8, -1.0) -> 1557.0,
        (3.0, -1.0, 0.5) -> 1557.0,
        (3.0, 0.5, 0.5) -> 1557.0,
        (3.0, 0.8, 0.5) -> 1557.0,
        (3.0, -1.0, 0.8) -> 1557.0,
        (3.0, 0.5, 0.8) -> 1557.0,
        (3.0, 0.8, 0.8) -> 1557.0,
      )

      for c <- Seq(0, 0.2, 3.0) do
        for itakura <- Seq(Double.NaN, 0.5, 0.8) do
          for window <- Seq(Double.NaN, 0.5, 0.8) do
            if !itakura.isNaN && !window.isNaN then
              // skip invalid combinations
              s"c=$c, itakuraMaxSlope=$itakura, window=$window" should {
                "throw an IllegalArgumentException because window and itakura bounding cannot be used at the same time" in {
                  assertThrows[IllegalArgumentException] {
                    MSM(c = c, itakuraMaxSlope = itakura, window = window)(x, y)
                  }
                }
              }
            else
              s"c=$c, itakuraMaxSlope=$itakura, window=$window" should {
                "produce the same results for case 1" in {
                  val windowKey = if window.isNaN then -1.0 else window
                  val itakuraKey = if itakura.isNaN then -1.0 else itakura
                  val distance = MSM(c = c, itakuraMaxSlope = itakura, window = window)(x, y)
                  distance shouldEqual expected(c, windowKey, itakuraKey)
                }
              }
    }
  }

  "The MSM pairwise distance" when {
    val msm = MSM(c = 1.0, itakuraMaxSlope = Double.NaN)

    "given two equal-length arrays" should {
      "be empty for two empty arrays" in {
        val x = Array.empty[Array[Double]]
        msm.pairwise(x) shouldEqual Array.empty[Array[Double]]
      }
      "be 0 for two identical arrays" in {
        val x = Array(
          Array(1.0, 2.0, 3.0),
          Array(1.0, 2.0, 3.0)
        )
        msm.pairwise(x) shouldEqual Array.tabulate(2, 2)((_, _) => 0.0)
      }
      "compute correctly" in {
        val x = Array(
          Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182),
          Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)
        )
        msm.pairwise(x) shouldEqual Array(Array(0.0, 1529.0), Array(1529.0, 0.0))
      }
    }
    "given two unequal-length arrays" should {
      "be 0 for an empty array and an array with one element" in {
        val x = Array(
          Array.empty[Double],
          Array(1.0)
        )
        msm.pairwise(x) shouldEqual Array.tabulate(2, 2)((_, _) => 0.0)
      }
      "compute correctly" in {
        val x = Array(
          Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182),
          Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114)
        )
        msm.pairwise(x) shouldEqual Array(Array(0.0, 2139.0), Array(2139.0, 0.0))
      }
    }
  }
  "compare to reference for Coffee dataset" in {
    val expectedDistanceMatrix = TestUtil.loadCSVFile("test-data/distance-matrix-coffee-msm.csv")
    val coffeeTrainData = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/Coffee/Coffee_TRAIN.ts"))
    val coffeeTestData = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/Coffee/Coffee_TEST.ts"))
    val data = coffeeTrainData ++ coffeeTestData
    val msm = MSM(c = 0.5, window = Double.NaN, itakuraMaxSlope = Double.NaN)

    val single01 = msm(data(0), data(1))
    single01 shouldEqual expectedDistanceMatrix(0)(1)

    val distanceMatrix = msm.pairwise(data)
    distanceMatrix.length shouldEqual expectedDistanceMatrix.length
    distanceMatrix shouldEqual expectedDistanceMatrix
  }
  "compare to reference for PickupGestureWiimoteZ dataset" in {
    val expectedDistanceMatrix = TestUtil.loadCSVFile("test-data/distance-matrix-PGWZ-msm.csv")
    val dataTrain = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TRAIN.ts"))
    val dataTest = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TEST.ts"))
    val data = dataTrain ++ dataTest
    val msm = MSM(c = 0.5, window = Double.NaN, itakuraMaxSlope = Double.NaN)

    val single01 = msm(data(0), data(1))
    single01 shouldEqual expectedDistanceMatrix(0)(1)

    val distanceMatrix = msm.pairwise(data)
    distanceMatrix.length shouldEqual expectedDistanceMatrix.length
    distanceMatrix shouldEqual expectedDistanceMatrix
  }

}
