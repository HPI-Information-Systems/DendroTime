package de.hpi.fgis.dendrotime.io.hierarchies

import com.univocity.parsers.csv.{CsvWriter, CsvWriterSettings}
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy

import java.io.File


/**
 * CSV Parser for reading input files and parsing them into a table representation.
 */
object HierarchyCSVWriter {
  def apply(): HierarchyCSVWriter = new HierarchyCSVWriter()
}

class HierarchyCSVWriter private {

  import HierarchyCSVWriter.*

  private val parserSettings = {
    val s = new CsvWriterSettings
    s.setHeaderWritingEnabled(false)
    val f = s.getFormat
    f.setDelimiter(",")
    f.setLineSeparator("\n")
    s
  }

  /**
   * Writes a hierarchy to a CSV file.
   *
   * @param file file name, can contain relative or absolute paths, see [[java.io.File]] for more infos
   * @param h    the hierarchy to write
   */
  def write(file: String, h: Hierarchy): Unit = write(new File(file), h)

  /**
   * Writes a hierarchy to a CSV file.
   *
   * @param file [[java.io.File]] pointing to the dataset
   * @param h    the hierarchy to write
   */
  def write(file: File, h: Hierarchy): Unit = {
    val writer = new CsvWriter(file, parserSettings)
    try
      h.fastForeach { level =>
        writer.writeRow(level.map(_.toString))
      }
    finally
      writer.close()
  }
}
