package de.hpi.fgis.dendrotime.evaluator.commands


import caseapp.{Command, Recurse, RemainingArgs}
import de.hpi.fgis.dendrotime.evaluator.{CommonArguments, Evaluator}

case class AverageAriOptions(@Recurse common: CommonArguments)

object AverageAri extends Command[AverageAriOptions] {

  override val name = "averageAri"

  def run(options: AverageAriOptions, args: RemainingArgs): Unit = Evaluator(options.common).averageAri()
}
