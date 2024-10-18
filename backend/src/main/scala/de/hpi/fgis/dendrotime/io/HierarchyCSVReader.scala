package de.hpi.fgis.dendrotime.io

import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}
import de.hpi.fgis.dendrotime.Settings

import scala.io.Codec


/**
 * CSV Parser for reading input files and parsing them into a table representation.
 */
object HierarchyCSVReader {

  def apply(): CSVParser = new HierarchyCSVReader()

}

class HierarchyCSVReader private() {

  private given fileCodec: Codec = Codec.UTF8

  private val parserSettings = {
    val s = new CsvParserSettings
    s.detectFormatAutomatically()
    s.setHeaderExtractionEnabled(false)
    s
  }

  /**
   * Reads a CSV file and parses it.
   *
   * @param file file name, can contain relative or absolute paths, see [[java.io.File]] for more infos
   * @return
   */
  def parse(file: String): Table = parse(new File(file))

  /**
   * Reads a CSV file and parses it.
   *
   * @param file [[java.io.File]] pointing to the dataset
   * @return
   */
  def parse(file: File): Table = {
    val p = ColumnProcessor(settings)
    val s = parserSettings.clone()
    s.setProcessor(p)
    val parser = new CsvParser(s)

    // parse and return result
    parser.parse(file)
    Table(
      name = extractFileName(settings.filePath),
      headers = p.headers,
      columns = p.columns
    )
  }

  private def extractFileName(path: String): String = {
    val filename = new File(path).getName
    filename.substring(0, filename.lastIndexOf("."))
  }
}