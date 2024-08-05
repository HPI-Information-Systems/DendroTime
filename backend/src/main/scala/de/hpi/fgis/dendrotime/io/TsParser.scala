package de.hpi.fgis.dendrotime.io

import java.io.*
import java.nio.charset.Charset
import scala.collection.mutable
import scala.util.Using


@main def testParser(): Unit = {
  val parser = TsParser(TsParser.TsParserSettings())
  val processor = new TsParser.TsProcessor {
    private var count = 0
    override def processMetadata(metadata: TsMetadata): Unit = {
      println(s"Metadata: $metadata")
    }

    override def processUnivariate(data: Array[Double], label: String): Unit = {
      count += 1
      println(s"Univariate TS $count with ${data.length} values and label '$label'")
    }
  }
  parser.parse(File("data/ACSF1/ACSF1_TEST.ts"), processor)
}

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
                               tsLimit: Option[Int] = None
                             )

  object TsProcessor {
    val default: TsProcessor = new TsProcessor {}
  }
  
  trait TsProcessor {
    def processMetadata(metadata: TsMetadata): Unit = {}

    def processUnivariate(data: Array[Double], label: String): Unit = {}
  }


  def apply(settings: TsParserSettings): TsParser = new TsParser(settings)
}

class TsParser(settings: TsParser.TsParserSettings) {

  private final val EOF = (-1).toChar
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
    Using.resource(new BufferedReader(new FileReader(file))) { input =>
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
        parseData(input, processor)
      else
        throw new IOException("Whitespace after @data annotation is not allowed!")
    }
  }

  private def parseMetadata(input: BufferedReader): (String, String) = {
    val key = StringBuilder()
    val value = StringBuilder()
    var parsingKey = true
    var parsingData = false

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

  private def parseData(input: BufferedReader, processor: TsParser.TsProcessor): Unit = {
    val ts = mutable.ListBuffer.empty[Double]
    var ch = input.read().toChar
    var nTimeseries = 0
    while ch != EOF do
      if ch != newLine && ch != ' ' then
        val value = StringBuilder()
        while ch != valueDelimiter && ch != channelDelimiter && ch != newLine do
          value.append(ch)
          ch = input.read().toChar
        if ch == valueDelimiter then
          ts.append(value.toString().strip.toDouble)
        else if ch == channelDelimiter then
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
        return
      ch = input.read().toChar
  }
}