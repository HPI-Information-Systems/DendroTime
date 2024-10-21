package de.hpi.fgis.dendrotime.io

import com.univocity.parsers.common.ParsingContext
import com.univocity.parsers.common.processor.AbstractRowProcessor
import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy

import java.io.File
import scala.collection.mutable


/**
 * CSV Parser for reading input files and parsing them into a table representation.
 */
object HierarchyCSVReader {

  def apply(): HierarchyCSVReader = new HierarchyCSVReader()

  private class ColumnProcessor extends AbstractRowProcessor {

    private val data: mutable.ArrayBuilder[Array[Double]] = mutable.ArrayBuilder.make[Array[Double]]
    private val reusableLineBuilder: mutable.ArrayBuilder[Double] = mutable.ArrayBuilder.ofDouble()

    def hierarchy: Array[Array[Double]] = data.result()

    override def rowProcessed(row: Array[String], context: ParsingContext): Unit = {
      for (j <- row.indices)
        reusableLineBuilder.addOne(row(j).toDouble)
      data.addOne(reusableLineBuilder.result())
      reusableLineBuilder.clear()
    }
  }
}

class HierarchyCSVReader private {

  private val parserSettings = {
    val s = new CsvParserSettings
    s.detectFormatAutomatically()
    s.setHeaderExtractionEnabled(false)
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
    val p = HierarchyCSVReader.ColumnProcessor()
    val s = parserSettings.clone()
    s.setProcessor(p)
    val parser = new CsvParser(s)

    // parse and return result
    parser.parse(file)
    Hierarchy.fromArray(p.hierarchy)
  }
}
