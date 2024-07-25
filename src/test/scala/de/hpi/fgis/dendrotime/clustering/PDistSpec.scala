package de.hpi.fgis.dendrotime.clustering

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class PDistSpec extends AnyWordSpec with should.Matchers {

  private val n = 4
  private val matrix = Array(
    Array(0.0, 1.0, 2.0, 3.0),
    Array(1.0, 0.0, 4.0, 5.0),
    Array(2.0, 4.0, 0.0, 6.0),
    Array(3.0, 5.0, 6.0, 0.0)
  )

  "The PDist object" should {
    "compute the index correctly" when {
      "n = 4" in {
        PDist.index(0, 1, 4) shouldEqual 0
        PDist.index(0, 2, 4) shouldEqual 1
        PDist.index(0, 3, 4) shouldEqual 2
        PDist.index(1, 2, 4) shouldEqual 3
        PDist.index(1, 3, 4) shouldEqual 4
        PDist.index(2, 3, 4) shouldEqual 5
      }
      "n = 5" in {
        PDist.index(0, 1, 5) shouldEqual 0
        PDist.index(0, 2, 5) shouldEqual 1
        PDist.index(0, 3, 5) shouldEqual 2
        PDist.index(0, 4, 5) shouldEqual 3
        PDist.index(1, 2, 5) shouldEqual 4
        PDist.index(1, 3, 5) shouldEqual 5
        PDist.index(1, 4, 5) shouldEqual 6
        PDist.index(2, 3, 5) shouldEqual 7
        PDist.index(2, 4, 5) shouldEqual 8
        PDist.index(3, 4, 5) shouldEqual 9
      }
    }

    "create a new empty pairwise distance vector of correct length" in {
      val dists = PDist.empty(n)
      dists.length shouldEqual n * (n - 1) / 2
      dists.n shouldEqual n
      dists.foreach(_ shouldEqual Double.PositiveInfinity)
    }

    "create a new pairwise distance vector from a dense matrix" in {
      val dists = PDist(matrix, n)
      dists.length shouldEqual n * (n - 1) / 2
      dists.n shouldEqual n
      for i <- 0 until n do
        for j <- i + 1 until n do
          dists(i, j) shouldEqual matrix(i)(j)
    }

    "create a new pairwise distance vector from a sparse matrix" in {
      val sparseMatrix = Array(
      Array(0.0, 0.0, 0.0, 0.0),
      Array(1.0, 0.0, 0.0, 0.0),
      Array(2.0, 4.0, 0.0, 0.0),
      Array(3.0, 5.0, 6.0, 0.0)
    )
      val dists = PDist(sparseMatrix, n)
      dists.length shouldEqual n * (n - 1) / 2
      dists.n shouldEqual n
      for i <- 0 until n do
        for j <- i + 1 until n do
          dists(i, j) shouldEqual sparseMatrix(i)(j)
    }
  }

  "A PDist instance" when {
    "immutable" should {
      val pdist = PDist(matrix, n)
      "be convertable to an immutable object" in {
        pdist.immutable shouldBe a[PDist]
        pdist.immutable shouldBe theSameInstanceAs(pdist)
      }
      "be copied to a mutable object" in {
        val copy = pdist.mutableCopy
        pdist shouldBe a[PDist]
        copy shouldBe a[MutablePDist]
        copy should not be theSameInstanceAs(pdist)
      }
      "return the correct distances" in {
        for i <- 0 until n do
          for j <- 0 until n do
            pdist(i, j) shouldEqual matrix(i)(j)
      }
    }

    "mutable" should {
      val pdist = PDist(matrix, n).mutable

      "be converted to an immutable object" in {
        pdist.immutable shouldBe a[PDist]
        pdist.immutable shouldBe theSameInstanceAs(pdist)
      }
      "allow updates of distances" in {
        val dists = pdist.mutableCopy
        dists(0, 1) = 1.0
        dists(0, 1) shouldEqual 1.0
        dists(3, 2) = 2.0
        dists(2, 3) shouldEqual 2.0
      }
      "reject updates of self-distances" in {
        val dists = pdist.mutableCopy
        val ex = intercept[RuntimeException] {
          dists(0, 0) = 1.0
        }
        ex shouldBe a[IllegalArgumentException]
        ex.getMessage should contain("Cannot set self-distance")
      }
    }
  }
}
