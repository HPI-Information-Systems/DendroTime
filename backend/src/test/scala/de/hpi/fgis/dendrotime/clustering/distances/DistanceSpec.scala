package de.hpi.fgis.dendrotime.clustering.distances

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class DistanceSpec extends AnyWordSpec with should.Matchers {

  import Distance.defaultOptions

  "The distance factory" should {
    "create a MSM distance with default options" in {
      val d = Distance("msm")
      d shouldBe a[MSM]
      val msm = d.asInstanceOf[MSM]
      msm.c shouldEqual MSM.DEFAULT_COST
      msm.window.isNaN shouldBe true
      msm.itakuraMaxSlope.isNaN shouldBe true
    }
    "create an SBD distance" in {
      val d = Distance("sbd")
      d shouldBe a[SBD]
      val sbd = d.asInstanceOf[SBD]
      sbd.standardize shouldEqual SBD.DEFAULT_STANDARDIZE
    }
    "throw an exception for an unknown distance" in {
      val name = "unknown"
      val ex = intercept[RuntimeException] {
        Distance(name)
      }
      ex shouldBe a[IllegalArgumentException]
      ex.getMessage should include (s"Distance $name is not implemented")
    }
  }
}
