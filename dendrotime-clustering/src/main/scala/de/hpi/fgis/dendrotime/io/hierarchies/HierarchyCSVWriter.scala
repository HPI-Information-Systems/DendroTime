package de.hpi.fgis.dendrotime.io.hierarchies

import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.io.CSVWriter

import java.io.File
import scala.util.Using


/**
 * CSV Parser for reading input files and parsing them into a table representation.
 */
object HierarchyCSVWriter {

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
  def write(file: File, h: Hierarchy): Unit =
    CSVWriter.write(file, h.backingArray)
}
