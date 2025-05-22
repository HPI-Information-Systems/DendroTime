package de.hpi.fgis.dendrotime.clustering.hierarchy

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class LinkageSpec extends AnyWordSpec with should.Matchers {

  "The Linkage factory" should {
    "create a SingleLinkage instance" in {
      val linkage = Linkage("single")
      linkage shouldEqual Linkage.SingleLinkage
    }
    "create a CompleteLinkage instance" in {
      val linkage = Linkage("complete")
      linkage shouldEqual Linkage.CompleteLinkage
    }
    "create an AverageLinkage instance" in {
      val linkage = Linkage("average")
      linkage shouldEqual Linkage.AverageLinkage
    }
    "create a WardLinkage instance" in {
      val linkage = Linkage("ward")
      linkage shouldEqual Linkage.WardLinkage
    }
    "create a WeightedLinkage instance" in {
      val linkage = Linkage("weighted")
      linkage shouldEqual Linkage.WeightedLinkage
    }
    "create a CentroidLinkage instance" in {
      val linkage = Linkage("centroid")
      linkage shouldEqual Linkage.CentroidLinkage
    }
    "create a MedianLinkage instance" in {
      val linkage = Linkage("median")
      linkage shouldEqual Linkage.MedianLinkage
    }
    "produce the correct error message for unknown linkages" in {
      val name = "unknown"
      val e = intercept[IllegalArgumentException] {
        Linkage(name)
      }
      e.getMessage shouldEqual f"Unknown linkage method: $name"
    }
  }

  "Linkage methods" should {
    val dXi = 3.0
    val dYi = 2.0
    val dXY = 1.0
    val nX = 2
    val nY = 3
    val nI = 2
    "compute the distance from a cluster i to the new cluster xy correctly" when {
      "using SingleLinkage" in {
        val distance = Linkage.SingleLinkage(dXi, dYi, dXY, nX, nY, nI)
        distance shouldEqual 2.0
      }
      "using CompleteLinkage" in {
        val distance = Linkage.CompleteLinkage(dXi, dYi, dXY, nX, nY, nI)
        distance shouldEqual 3.0
      }
      "using AverageLinkage" in {
        val distance = Linkage.AverageLinkage(dXi, dYi, dXY, nX, nY, nI)
        distance shouldEqual 2.4
      }
      "using WardLinkage" in {
        val distance = Linkage.WardLinkage(dXi, dYi, dXY, nX, nY, nI)
        distance shouldEqual 2.77 +- 0.01
      }
      "using WeightedLinkage" in {
        val distance = Linkage.WeightedLinkage(dXi, dYi, dXY, nX, nY, nI)
        distance shouldEqual 2.5
      }
      "using MedianLinkage" in {
        val distance = Linkage.MedianLinkage(dXi, dYi, dXY, nX, nY, nI)
        distance shouldEqual 2.5
      }
      "using CentroidLinkage" in {
        val distance = Linkage.CentroidLinkage(dXi, dYi, dXY, nX, nY, nI)
        distance shouldEqual 2.4
      }
    }
  }
}
