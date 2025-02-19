package de.hpi.fgis.dendrotime.io

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

class TsParserSpec extends AnyWordSpec with should.Matchers {
  private val equalLengthFile = new File(
    getClass.getClassLoader.getResource("test-data/Coffee_TEST.ts").getPath
  )
  private val variableLengthFile = new File(
    getClass.getClassLoader.getResource("test-data/PickupGestureWiimoteZ_TRAIN.ts").getPath
  )

  "The TsParser" when {
    "default settings" should {
      "parse equal-length classification .ts-files from aeon successfully" in {
        val parser = TsParser(TsParser.TsParserSettings())
        val p = Processor()
        parser.parse(equalLengthFile, p)
        p.firstMethod shouldBe "processMetadata"
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
        p.timeseries.length shouldBe 28
        for ts <- p.timeseries do
          ts.length shouldBe 286
      }

      "parse variable-length classification .ts-files from aeon successfully" in {
        val parser = TsParser(TsParser.TsParserSettings())
        val p = Processor()
        parser.parse(variableLengthFile, p)
        p.firstMethod shouldBe "processMetadata"
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
        p.timeseries.length shouldBe 50
      }
    }

    "metadata extraction is disabled" should {
      "skip metadata and set nTimeseries correctly" in {
        val parser = TsParser(TsParser.TsParserSettings(parseMetadata = false))
        val p = Processor()
        parser.parse(variableLengthFile, p)
        p.firstMethod shouldBe "processUnivariate"
        p.nTimeseries shouldBe 50
        p.metadata shouldBe None
        p.timeseries.length shouldBe 50
      }
    }

    "a time series limit is set" should {
      "stop parsing after the limit is reached" in {
        val parser = TsParser(TsParser.TsParserSettings(tsLimit = Some(10)))
        val p = Processor()
        parser.parse(variableLengthFile, p)
        p.firstMethod shouldBe "processMetadata"
        p.nTimeseries shouldBe 10
        p.timeseries.length shouldBe 10
      }
    }
  }

  private final class Processor extends TsParser.TsProcessor {
    var firstMethod = ""
    var metadata: Option[TsMetadata] = None
    var nTimeseries: Int = _

    private val tsBuilder = Array.newBuilder[Array[Double]]

    lazy val timeseries: Array[Array[Double]] = tsBuilder.result()

    override def processMetadata(metadata: TsMetadata): Unit = {
      this.metadata = Some(metadata)
      if (firstMethod.isEmpty) firstMethod = "processMetadata"
    }

    override def processUnivariate(data: Array[Double], label: String): Unit = {
      tsBuilder.addOne(data)
      if (firstMethod.isEmpty) firstMethod = "processUnivariate"
    }

    override def processTSCount(nTimeseries: Int): Unit = {
      this.nTimeseries = nTimeseries
      if (firstMethod.isEmpty) firstMethod = "processTSCount"
    }
  }
}
