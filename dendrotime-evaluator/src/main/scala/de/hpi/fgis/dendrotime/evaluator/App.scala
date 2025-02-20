package de.hpi.fgis.dendrotime.evaluator

import caseapp.AppName
import caseapp.core.app.{Command, CommandsEntryPoint}
import de.hpi.fgis.dendrotime.evaluator.commands.*

@AppName("DendroTime Evaluator")
object App extends CommandsEntryPoint {
  override val progName: String = "evaluator"
  override val description: String = "Evaluator for dendrograms"

  override val commands: Seq[Command[?]] = Seq(
    AriAt,
    //    AmiAt,
    LabelChangesAt,
    AverageAri,
    ApproxAverageAri,
    HierarchySimilarity,
    WeightedHierarchySimilarity
  )
}
