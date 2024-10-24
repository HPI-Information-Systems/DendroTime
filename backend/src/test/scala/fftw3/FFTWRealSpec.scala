package fftw3

import de.hpi.fgis.dendrotime.TestUtil
import org.scalatest.concurrent.Conductors
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.Executors
import scala.concurrent.duration.given
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

class FFTWRealSpec extends AnyWordSpec with should.Matchers with TestUtil.ImplicitEqualitySupport with Conductors {

  "FFTWReal" should {
    "convolve two equal-length arrays" in {
      val a = Array[Double](1, 2, 3, 4, 5)
      val b = Array[Double](6, 7, 8, 9, 10)
      val result = FFTWReal.fftwConvolve(a, b)
      result shouldEqual Array[Double](10, 29, 56, 90, 130, 110, 86, 59, 30)
    }
    "convolve two different-length arrays" in {
      val a = Array[Double](1, 2, 3, 4, 5)
      val b = Array[Double](6, 7, 8)
      val result = FFTWReal.fftwConvolve(a, b)
      result shouldEqual Array[Double](8, 23, 44, 65, 86, 59, 30)
    }
    "work in multiple threads" in {
      given ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))

      val n = 100
      val a = Array.fill(1000)(scala.util.Random.nextDouble)
      val b = Array.fill(700)(scala.util.Random.nextDouble)

      val threads = (0 until n).map(_ => Future {
        val result = FFTWReal.fftwConvolve(a, b)
        result.length shouldEqual 1699
      })
      Await.ready(Future.sequence(threads), 1 second)
    }
  }
}
