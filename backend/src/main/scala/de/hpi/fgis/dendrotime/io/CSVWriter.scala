package de.hpi.fgis.dendrotime.io

import com.univocity.parsers.csv.{CsvWriter, CsvWriterSettings}

import java.io.File
import scala.util.Using


/**
 * CSV Writer for 2D tabular data. No headers are allowed, and data must be separated by commas.
 * This writer only writes decimal numbers (Doubles) in a 2D array.
 */
object CSVWriter {

  given Using.Releasable[CsvWriter] with
    def release(resource: CsvWriter): Unit = resource.close()

  private val parserSettings = {
    val s = new CsvWriterSettings
    s.setHeaderWritingEnabled(false)
    val f = s.getFormat
    f.setDelimiter(",")
    f.setLineSeparator("\n")
    s
  }

  /**
   * Writes a 2D array to a CSV file.
   *
   * @param file file name, can contain relative or absolute paths, see [[java.io.File]] for more infos
   * @param data the 2D array to write
   */
  def write[T <: AnyVal | String](file: String, data: Array[Array[T]]): Unit = write(new File(file), data)

  /**
   * Writes a 2D array to a CSV file.
   *
   * @param file [[java.io.File]] pointing to the dataset
   * @param data the 2D array to write
   */
  def write[T <: AnyVal | String](file: File, data: Array[Array[T]]): Unit = internalWrite(file, data, parserSettings)

  /**
   * Writes a 2D array to a CSV file with the given header.
   *
   * @param file   file name, can contain relative or absolute paths, see [[java.io.File]] for more infos
   * @param data   the 2D array to write
   * @param header the header to write
   */
  def write[T <: AnyVal | String](file: String, data: Array[Array[T]], header: Array[String]): Unit =
    write(new File(file), data, header)

  /**
   * Writes a 2D array to a CSV file with the given header.
   *
   * @param file   [[java.io.File]] pointing to the dataset
   * @param data   the 2D array to write
   * @param header the header to write
   */
  def write[T <: AnyVal | String](file: File, data: Array[Array[T]], header: Array[String]): Unit = {
    val settings = parserSettings.clone()
    settings.setHeaderWritingEnabled(true)
    settings.setHeaders(header: _*)
    internalWrite(file, data, settings)
  }

  private def internalWrite[T <: AnyVal | String](file: File, data: Array[Array[T]], settings: CsvWriterSettings): Unit = {
    Using.resource(new CsvWriter(file, settings)) { writer =>
      for row <- data do
        writer.writeRow(row.map(_.toString))
    }
  }
}
