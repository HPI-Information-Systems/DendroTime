package de.hpi.fgis.dendrotime.clustering.metrics

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random

class AdjustedRandScoreSpec extends AnyWordSpec with should.Matchers {
  "The AdjustedRandScore" should {
    "return 1.0 for identical labelings" in {
      val trueLabels = Array(0,0,1,1)
      val predLabels = Array(0,0,1,1)
      SupervisedClustering.ari(trueLabels, predLabels) shouldEqual 1.0
    }
    "return penalized score for single cluster" in {
      val trueLabels = Array(0, 0, 1, 2)
      val predLabels = Array(0, 0, 1, 1)
      SupervisedClustering.ari(trueLabels, predLabels) shouldEqual 0.5714285714285714
    }
    "be symmetric" in {
      val trueLabels = Array(0, 0, 1, 1)
      val predLabels = Array(0, 0, 1, 2)
      SupervisedClustering.ari(trueLabels, predLabels) shouldEqual 0.5714285714285714
      SupervisedClustering.ari(trueLabels, predLabels) shouldEqual SupervisedClustering.ari(predLabels, trueLabels)
    }
    "return 0.0 for split clusters" in {
      val trueLabels = Array(0, 0, 0, 0)
      val predLabels = Array(0, 1, 2, 3)
      SupervisedClustering.ari(trueLabels, predLabels) shouldEqual 0.0
    }
    "return negative values for discordant labelings" in {
      val trueLabels = Array(0, 0, 1, 1)
      val predLabels = Array(0, 1, 0, 1)
      SupervisedClustering.ari(trueLabels, predLabels) shouldEqual -0.5
    }
    "not overflow" in {
      val r = Random(0)
      val trueLabels = Array.fill(100000){r.nextInt(2)}
      val predLabels = Array.fill(100000){r.nextInt(2)}
      val score = SupervisedClustering.ari(trueLabels, predLabels)
      score should be >= -0.5
      score should be <= 1.0
    }
    "work for Strings" in {
      val trueLabels = Array("a", "a", "b", "b")
      val predLabels = Array("a", "a", "b", "b")
      SupervisedClustering.ari(trueLabels, predLabels) shouldEqual 1.0
    }
  }
}
