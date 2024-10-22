package de.hpi.fgis.dendrotime.io

import java.io.File
import de.hpi.fgis.dendrotime.TestUtil
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class TsParserSpec extends AnyWordSpec with should.Matchers {
  private val equalLengthFile = File(TestUtil.findResource("test-data/datasets/Coffee/Coffee_TEST.ts"))
  private val variableLengthFile = File(TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TRAIN.ts"))

  "The TsParser" when {
    "default settings" should {
      "parse equal-length classification .ts-files from aeon successfully" in {
        val parser = TsParser(TsParser.TsParserSettings())
        val p = CountingProcessor()
        parser.parse(equalLengthFile, p)
        p.firstMethod shouldBe "processMetadata"
        p.count shouldBe 28
        p.nTimeseries shouldBe 28
        p.metadata.get shouldEqual TsClassificationMetadata(
          missing = false,
          univariate = true,
          dimension = 1,
          equalLength = true,
          timestamps = false,
          problemName = "Coffee",
          seriesLength = 286,
          classLabel = true
        )
      }

      "parse variable-length classification .ts-files from aeon successfully" in {
        val parser = TsParser(TsParser.TsParserSettings())
        val p = CountingProcessor()
        parser.parse(variableLengthFile, p)
        p.firstMethod shouldBe "processMetadata"
        p.count shouldBe 50
        p.nTimeseries shouldBe 50
        p.metadata.get shouldEqual TsClassificationMetadata(
          missing = false,
          univariate = true,
          dimension = 1,
          equalLength = false,
          timestamps = false,
          problemName = "PickupGestureWiimoteZ",
          seriesLength = 0,
          classLabel = true
        )
      }
    }

    "metadata extraction is disabled" should {
      "skip metadata and set nTimeseries correctly" in {
        val parser = TsParser(TsParser.TsParserSettings(parseMetadata = false))
        val p = CountingProcessor()
        parser.parse(variableLengthFile, p)
        p.firstMethod shouldBe "processUnivariate"
        p.count shouldBe 50
        p.nTimeseries shouldBe 50
        p.metadata shouldBe None
      }
    }

    "a time series limit is set" should {
      "stop parsing after the limit is reached" in {
        val parser = TsParser(TsParser.TsParserSettings(tsLimit = Some(10)))
        val p = CountingProcessor()
        parser.parse(variableLengthFile, p)
        p.firstMethod shouldBe "processMetadata"
        p.count shouldBe 10
        p.nTimeseries shouldBe 10
      }
    }
  }

  private final class CountingProcessor extends TsParser.TsProcessor {
    var firstMethod = ""
    var count: Int = 0
    var metadata: Option[TsMetadata] = None
    var nTimeseries: Int = _

    override def processMetadata(metadata: TsMetadata): Unit = {
      this.metadata = Some(metadata)
      if (firstMethod.isEmpty) firstMethod = "processMetadata"
    }

    override def processUnivariate(data: Array[Double], label: String): Unit = {
      count += 1
      if (firstMethod.isEmpty) firstMethod = "processUnivariate"
    }

    override def processTSCount(nTimeseries: Int): Unit = {
      this.nTimeseries = nTimeseries
      if (firstMethod.isEmpty) firstMethod = "processTSCount"
    }
  }
}
