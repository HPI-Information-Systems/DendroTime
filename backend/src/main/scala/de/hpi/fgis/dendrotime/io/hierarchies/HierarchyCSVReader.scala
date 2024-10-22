package de.hpi.fgis.dendrotime.io.hierarchies

import com.univocity.parsers.common.{IterableResult, ParsingContext}
import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy

import java.io.File
import scala.collection.mutable


/**
 * CSV Parser for reading input files and parsing them into a table representation.
 */
object HierarchyCSVReader {
  def apply(): HierarchyCSVReader = new HierarchyCSVReader()

  extension [T](result: IterableResult[T, ParsingContext])
    def foreach(f: T => Unit): Unit =
      result.forEach(f(_))
}

class HierarchyCSVReader private {

  import HierarchyCSVReader.*

  private val parserSettings = {
    val s = new CsvParserSettings
    s.setHeaderExtractionEnabled(false)
    val f = s.getFormat
    f.setDelimiter(",")
    f.setLineSeparator("\n")
    s
  }

  /**
   * Reads a CSV file containing a hierarchy and parses it.
   *
   * @param file file name, can contain relative or absolute paths, see [[java.io.File]] for more infos
   * @return the parsed hierarchy
   */
  def parse(file: String): Hierarchy = parse(new File(file))

  /**
   * Reads a CSV file containing a hierarchy  and parses it.
   *
   * @param file [[java.io.File]] pointing to the dataset
   * @return the parsed hierarchy
   */
  def parse(file: File): Hierarchy = {
    val parser = new CsvParser(parserSettings)
    val data = mutable.ArrayBuilder.make[Array[Double]]
    val reusableLineBuilder = mutable.ArrayBuilder.ofDouble()

    try
      for row <- parser.iterate(file) do
        reusableLineBuilder.addAll(row.map(_.toDouble))
        data.addOne(reusableLineBuilder.result())
        reusableLineBuilder.clear()
      Hierarchy.fromArray(data.result())
    finally
      parser.stopParsing()
  }
}
