package de.hpi.fgis.dendrotime.io

import com.univocity.parsers.common.{IterableResult, ParsingContext}
import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}

import java.io.File
import scala.collection.mutable


/**
 * CSV Parser for 2D tabular data. No headers are allowed, and data must be separated by commas.
 * This parser only reads decimal numbers (Doubles).
 */
object CSVReader {

  extension [T](result: IterableResult[T, ParsingContext])
    def foreach(f: T => Unit): Unit =
      result.forEach(f(_))

  private val parserSettings = {
    val s = new CsvParserSettings
    s.setHeaderExtractionEnabled(false)
    val f = s.getFormat
    f.setDelimiter(",")
    f.setLineSeparator("\n")
    s
  }

  /**
   * Reads a CSV file and parses it.
   *
   * @param file file name, can contain relative or absolute paths, see [[java.io.File]] for more infos
   * @return the parsed dataset as a 2D array
   */
  def parse(file: String): Array[Array[Double]] = parse(new File(file))

  /**
   * Reads a CSV file and parses it.
   *
   * @param file [[java.io.File]] pointing to the dataset
   * @return the parsed dataset as a 2D array
   */
  def parse(file: File): Array[Array[Double]] = {
    val parser = new CsvParser(parserSettings)
    val data = mutable.ArrayBuilder.make[Array[Double]]
    val reusableLineBuilder = mutable.ArrayBuilder.ofDouble()

    try
      for row <- parser.iterate(file) do
        reusableLineBuilder.addAll(row.map(_.toDouble))
        data.addOne(reusableLineBuilder.result())
        reusableLineBuilder.clear()
      data.result()
    finally
      parser.stopParsing()
  }
}
