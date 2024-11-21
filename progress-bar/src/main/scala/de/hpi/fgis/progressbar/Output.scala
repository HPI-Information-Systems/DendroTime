package de.hpi.fgis.progressbar

@FunctionalInterface
trait Output {
  def apply(s: Any): Unit
}

object Output {
  given consoleOutput: Output = Console.print
}
