package de.hpi.fgis.dendrotime

object TestUtil {
  /** Returns the path to a file in the test-resources folder. */
  def findResource(filepath: String): String =
    getClass.getClassLoader.getResource(filepath).getPath
}
