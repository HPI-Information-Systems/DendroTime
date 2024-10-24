package de.hpi.fgis.dendrotime.io.hierarchies

import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.io.CSVReader

import java.io.File


/**
 * CSV Parser for reading input files and parsing them into a table representation.
 */
object HierarchyCSVReader {
  def apply(): HierarchyCSVReader = new HierarchyCSVReader()
}

class HierarchyCSVReader private {

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
    val data = CSVReader.parse(file)
    Hierarchy.fromArray(data)
  }
}
