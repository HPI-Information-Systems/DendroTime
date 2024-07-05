package de.hpi.fgis.dendrotime.io

import java.io.*
import scala.collection.mutable
import scala.util.Using


@main def testParser(): Unit = {
  val parser = TsParser(TsParser.TsParserSettings(), new TsParser.TsProcessor {
    private var count = 0
    override def processMetadata(metadata: TsMetadata): Unit = {
      println(s"Metadata: $metadata")
    }

    override def processUnivariate(data: Array[Double], label: String): Unit = {
      count += 1
      println(s"Univariate TS $count with ${data.length} values and label '$label'")
    }
  }
  )
  parser.parse(File("data/ACSF1/ACSF1_TEST.ts"))
}

object TsParser {

  case class TsFormat(
                       newLine: Char = '\n',
                       valueDelimiter: Char = ',',
                       channelDelimiter: Char = ':',
                       metadataIdentifier: Char = '@',
                       commentIdentifier: Char = '#'
                     )

  case class TsParserSettings(format: TsFormat = TsFormat(), parseMetadata: Boolean = true)

  trait TsProcessor {
    def processMetadata(metadata: TsMetadata): Unit = {}

    def processUnivariate(data: Array[Double], label: String): Unit = {}
  }


  def apply(settings: TsParserSettings): TsParser = new TsParser(settings, new TsProcessor {})

  def apply(settings: TsParserSettings, processor: TsProcessor): TsParser = new TsParser(settings, processor)
}

class TsParser(settings: TsParser.TsParserSettings, processor: TsParser.TsProcessor) {

  private val newLine = settings.format.newLine
  private val valueDelimiter = settings.format.valueDelimiter
  private val channelDelimiter = settings.format.channelDelimiter
  private val metadataIdentifier = settings.format.metadataIdentifier
  private val commentIdentifier = settings.format.commentIdentifier

  private val metadata: mutable.Map[String, String] = mutable.Map.empty
  private var parsingData = false

  def parse(file: File): Unit = {
    //    println(s"Starting parsing file ${file.getName}")
    Using.resource(new BufferedReader(new FileReader(file))) { input =>
      var ch: Char = input.read().toChar
      while ch != -1 && !parsingData do
        if ch == commentIdentifier then
          // skip comment
          //          println("Skipping comment")
          input.readLine()
        else if ch == metadataIdentifier then
          // parse metadata
          parseMetadata(input)
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
        parseData(input)
      else
        throw new IOException("Whitespace after @data annotation is not allowed!")
    }
  }

  private def parseMetadata(input: BufferedReader): Unit = {
    val key = StringBuilder()
    val value = StringBuilder()
    var parsingKey = true

    var ch = input.read().toChar
    while ch != newLine && ch != -1 do
      if ch == ' ' then
        parsingKey = false
      else if parsingKey then
        key.append(ch)
      else
        value.append(ch)
      ch = input.read().toChar
    val keyName = key.toString()
    if keyName.toLowerCase == "data" then
      parsingData = true
    else if settings.parseMetadata then
      metadata(keyName) = value.toString().strip()
  }

  private def parseData(input: BufferedReader): Unit = {
    val ts = mutable.ListBuffer.empty[Double]
    var ch = input.read().toChar
    while ch != -1 do
      if ch != newLine && ch != ' ' then
        val value = StringBuilder()
        while ch != valueDelimiter && ch != channelDelimiter && ch != newLine && ch != -1 do
          value.append(ch)
          ch = input.read().toChar
        if ch == valueDelimiter then
          ts.append(value.toString().strip.toDouble)
        else if ch == channelDelimiter then
          // FIXME: only supports univariate TS at the moment
          val label = input.readLine()
          processor.processUnivariate(ts.toArray, label)
          ts.clear()
        else
          if ts.nonEmpty then
            processor.processUnivariate(ts.toArray, "")
            ts.clear()
      ch = input.read().toChar
  }
}