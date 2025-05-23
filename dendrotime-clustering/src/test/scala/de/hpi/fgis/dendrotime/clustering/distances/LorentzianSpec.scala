package de.hpi.fgis.dendrotime.clustering.distances

import de.hpi.fgis.dendrotime.clustering.TestUtil
import org.scalatest.matchers.*
import org.scalatest.wordspec.AnyWordSpec

class LorentzianSpec extends AnyWordSpec with should.Matchers {

  import TestUtil.ImplicitEqualities.given

  "The normalized Lorentzian distance" when {
    val lorentzian = Lorentzian(normalize = true)
    "given two equal-length arrays" should {
      "be 0 for two empty arrays" in {
        val x = Array.empty[Double]
        val y = Array.empty[Double]
        lorentzian(x, y) shouldEqual 0.0
      }
      "be 0 for two identical arrays" in {
        val x = Array(1.0, 2.0, 3.0, 4.0)
        lorentzian(x, x.clone()) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)
        lorentzian(x, y) shouldEqual 0.527078951 +- 1e-9
      }
    }
    "given two unequal-length arrays" should {
      "be 0 for an empty array and an array with one element" in {
        val x = Array.empty[Double]
        val y = Array(1.0)
        lorentzian(x, y) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114)
        lorentzian(x, y) shouldEqual 0.443160032 +- 1e-9
      }
    }
  }

  "The non-normalized Lorentzian distance" when {
    val lorentzian = Lorentzian(normalize = false)
    "given two equal-length arrays" should {
      "be 0 for two empty arrays" in {
        val x = Array.empty[Double]
        val y = Array.empty[Double]
        lorentzian(x, y) shouldEqual 0.0
      }
      "be 0 for two identical arrays" in {
        val x = Array(1.0, 2.0, 3.0, 4.0)
        lorentzian(x, x.clone()) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)
        lorentzian(x, y) shouldEqual 96.727030642 +- 1e-9
      }
    }
    "given two unequal-length arrays" should {
      "be 0 for an empty array and an array with one element" in {
        val x = Array.empty[Double]
        val y = Array(1.0)
        lorentzian(x, y) shouldEqual 0.0
      }
      "compute correctly" in {
        val x = Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182)
        val y = Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114)
        lorentzian(x, y) shouldEqual 81.114101657 +- 1e-9
      }
    }
  }

  "The Lorentzian pairwise distance" when {
    val lorentzian = Lorentzian(normalize = true)
    "given two equal-length arrays" should {
      "be empty for two empty arrays" in {
        val x = Array.empty[Array[Double]]
        lorentzian.pairwise(x) shouldEqual Array.empty[Array[Double]]
      }
      "be 0 for two identical arrays" in {
        val x = Array(
          Array(1.0, 2.0, 3.0, 4.0),
          Array(1.0, 2.0, 3.0, 4.0)
        )
        lorentzian.pairwise(x) shouldEqual Array.tabulate(2, 2)((_, _) => 0.0)
      }
      "compute correctly" in {
        val x = Array(
          Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182),
          Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114, 814, 635, 304, 168)
        )
        val expected = Array(Array(0.0, 0.527078951), Array(0.527078951, 0.0))
        lorentzian.pairwise(x) shouldEqual expected
      }
    }
    "given two unequal-length arrays" should {
      "be 0 for an empty array and an array with one element" in {
        val x = Array(
          Array.empty[Double],
          Array(1.0)
        )
        lorentzian.pairwise(x) shouldEqual Array.tabulate(2, 2)((_, _) => 0.0)
      }
      "compute correctly" in {
        val x = Array(
          Array[Double](573, 375, 301, 212, 55, 34, 25, 33, 113, 143, 303, 615, 1226, 1281, 1221, 1081, 866, 1096, 1039, 975, 746, 581, 409, 182),
          Array[Double](603, 348, 176, 177, 47, 30, 40, 42, 101, 180, 401, 777, 1344, 1573, 1408, 1243, 1141, 1178, 1256, 1114)
        )
        val expected = Array(Array(0.0, 0.443160032), Array(0.443160032, 0.0))
        lorentzian.pairwise(x) shouldEqual expected
      }
    }
    "compare to reference implementation for Coffee dataset" in {
      val expectedDistanceMatrix = TestUtil.loadCSVFile("test-data/distance-matrix-coffee-lorentzian.csv")
      val coffeeTrainData = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/Coffee/Coffee_TRAIN.ts"))
      val coffeeTestData = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/Coffee/Coffee_TEST.ts"))
      val data = coffeeTrainData ++ coffeeTestData
      val lorentzian = Lorentzian(normalize = false)

      val single01 = lorentzian(data(0), data(1))
      single01 shouldEqual expectedDistanceMatrix(0)(1)

      val distanceMatrix = lorentzian.pairwise(data)
      distanceMatrix.length shouldEqual expectedDistanceMatrix.length
      distanceMatrix shouldEqual expectedDistanceMatrix
    }
    "compare to reference for PickupGestureWiimoteZ dataset" in {
      val expectedDistanceMatrix = TestUtil.loadCSVFile("test-data/distance-matrix-PGWZ-lorentzian.csv")
      val dataTrain = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TRAIN.ts"))
      val dataTest = TestUtil.loadDataset(TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TEST.ts"))
      val data = dataTrain ++ dataTest
      val lorentzian = Lorentzian(normalize = false)

      val single01 = lorentzian(data(0), data(1))
      single01 shouldEqual expectedDistanceMatrix(0)(1)

      val distanceMatrix = lorentzian.pairwise(data)
      distanceMatrix.length shouldEqual expectedDistanceMatrix.length
      distanceMatrix shouldEqual expectedDistanceMatrix
    }
  }
}
