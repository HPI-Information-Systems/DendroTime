package de.hpi.fgis.dendrotime.io

import java.io.*
import scala.collection.mutable
import scala.util.Using


@main def testParser(): Unit = {
  val parser = TsParser(TsParser.TsParserSettings())
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

  case class TsParserSettings(format: TsFormat = TsFormat(), parseMetadata: Boolean = false)

  def apply(settings: TsParserSettings): TsParser = new TsParser(settings)
}

class TsParser(settings: TsParser.TsParserSettings) {

  private val newLine = settings.format.newLine
  private val valueDelimiter = settings.format.valueDelimiter
  private val channelDelimiter = settings.format.channelDelimiter
  private val metadataIdentifier = settings.format.metadataIdentifier
  private val commentIdentifier = settings.format.commentIdentifier

  private val metadata: mutable.Map[String, String] = mutable.Map.empty
  private var parsingData = false

  def parse(file: File): Unit = {
    println(s"Starting parsing file ${file.getName}")
    Using.resource(new BufferedReader(new FileReader(file))) { input =>
      var ch: Char = input.read().toChar
      while ch != -1 do
        if ch == commentIdentifier then
          // skip comment
          println("Skipping comment")
          input.readLine()
        else if ch == metadataIdentifier then
          // parse metadata
          parseMetadata(input)
        else if ch != ' ' then
          if parsingData then
            // parse data
            parseData(input)
          else
            throw new IOException("Data section not found!")
        else
          // skip whitespace
          println("Skipping whitespace")
        ch = input.read().toChar
    }
  }

  private def parseMetadata(input: BufferedReader): Unit = {
    println("Parsing metadata")
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
    else
      metadata(keyName) = value.toString().strip()
  }

  private def parseData(input: BufferedReader): Unit = {
    println("Parsing data")
    println(input.readLine())
  }
}