package de.hpi.fgis.dendrotime.io

import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

import java.io.*
import java.nio.charset.Charset
import scala.collection.mutable
import scala.util.Using

object TsParser {

  case class TsFormat(
                       newLine: Char = '\n',
                       valueDelimiter: Char = ',',
                       channelDelimiter: Char = ':',
                       metadataIdentifier: Char = '@',
                       commentIdentifier: Char = '#'
                     )

  case class TsParserSettings(
                               format: TsFormat = TsFormat(),
                               parseMetadata: Boolean = true,
                               encoding: String = "UTF-8",
                               tsLimit: Option[Int] = None,
                             )

  object TsProcessor {
    val default: TsProcessor = new TsProcessor {}
  }
  
  trait TsProcessor {
    def processMetadata(metadata: TsMetadata): Unit = {}

    def processUnivariate(data: Array[Double], label: String): Unit = {}
    
    def processTSCount(nTimeseries: Int): Unit = {}
  }

  def apply(settings: TsParserSettings): TsParser = new TsParser(settings)

  def loadAllLabeledTimeSeries(file: File,
                               settings: TsParserSettings = TsParser.TsParserSettings(parseMetadata = false),
                               idOffset: Int = 0): Array[LabeledTimeSeries] = {
    val parser = TsParser(settings)
    var idx = 0
    val builder = mutable.ArrayBuilder.make[LabeledTimeSeries]
    val processor = new TsParser.TsProcessor {
      override def processTSCount(nTimeseries: Int): Unit =
        builder.sizeHint(nTimeseries)
      override def processUnivariate(data: Array[Double], label: String): Unit = {
        val ts = LabeledTimeSeries(idOffset + idx, idOffset + idx, data, label)
        builder += ts
        idx += 1
      }
    }
    parser.parse(file, processor)
    builder.result()
  }
}

class TsParser(settings: TsParser.TsParserSettings) {

  private final val EOF = -1.toChar
  private val charset = Charset.forName(settings.encoding)
  private val newLine = settings.format.newLine
  private val valueDelimiter = settings.format.valueDelimiter
  private val channelDelimiter = settings.format.channelDelimiter
  private val metadataIdentifier = settings.format.metadataIdentifier
  private val commentIdentifier = settings.format.commentIdentifier

  def parse(file: File, processor: TsParser.TsProcessor = TsParser.TsProcessor.default): Unit = {
    var parsingData = false
    val metadata: mutable.Map[String, String] = mutable.Map.empty
    
//    println(s"Starting parsing file ${file.getName}")
    Using.resource(new BufferedReader(new FileReader(file, charset))) { input =>
      var ch: Char = input.read().toChar
      while ch != EOF && !parsingData do
        if ch == commentIdentifier then
          // skip comment
//          println("Skipping comment")
          input.readLine()
        else if ch == metadataIdentifier then
          // parse metadata
          val (key, value) = parseMetadata(input)
          if key.toLowerCase == "data" then
            parsingData = true
          else if settings.parseMetadata then
            metadata(key) = value.strip()
        else if ch > ' ' then
          throw new IOException(s"Unexpected character '$ch' in header!")
//        else
        // skip whitespace
//          println("Skipping whitespace")
        ch = input.read().toChar

      if !parsingData then
        throw new IOException("Data section not found!")
      else if ch != ' ' then
        // forward metadata to processor before switching parsing state
        if settings.parseMetadata then
          processor.processMetadata(TsMetadata(metadata.toMap))
        // parse data
        val nTimeseries = parseData(input, processor, ch)
        processor.processTSCount(nTimeseries)
      else
        throw new IOException("Whitespace after @data annotation is not allowed!")
    }
  }
  
  def countTimeseries(file: File): Int = {
    Using.resource(new BufferedReader(new FileReader(file, charset))) { input =>
      // find @data annotation and then count the number lines
      var lineCounter = 0
      var dataIdx = -1
      input.lines().forEach { line =>
        lineCounter += 1
        if line.startsWith("@data") then
          dataIdx = lineCounter
        if dataIdx > 0 && line.isEmpty then
          lineCounter -= 1
      }
      lineCounter - dataIdx
    }
  }

  private def parseMetadata(input: BufferedReader): (String, String) = {
    val key = StringBuilder()
    val value = StringBuilder()
    var parsingKey = true

    var ch = input.read().toChar
    while ch != newLine && ch != EOF do
      if ch == ' ' then
        parsingKey = false
      else if parsingKey then
        key.append(ch)
      else
        value.append(ch)
      ch = input.read().toChar
    (key.toString(), value.toString())
  }

  private def parseData(input: BufferedReader, processor: TsParser.TsProcessor, currentCh: Char): Int = {
    val ts = mutable.ListBuffer.empty[Double]
    var ch = currentCh
    var nTimeseries = 0
    while ch != EOF do
      if ch != newLine && ch != ' ' then
        val value = StringBuilder()
        while ch != valueDelimiter && ch != channelDelimiter && ch != newLine do
          value.append(ch)
          ch = input.read().toChar
        if ch == valueDelimiter then
          ts.append(value.toString().toDouble)
        else if ch == channelDelimiter then
          ts.append(value.toString().toDouble)
          // FIXME: only supports univariate TS at the moment
          val label = input.readLine()
          processor.processUnivariate(ts.toArray, label)
          nTimeseries += 1
          ts.clear()
        else
          if ts.nonEmpty then
            processor.processUnivariate(ts.toArray, "")
            nTimeseries += 1
            ts.clear()
            
      if settings.tsLimit.exists(nTimeseries >= _) then
        return nTimeseries
      ch = input.read().toChar
    nTimeseries
  }
}