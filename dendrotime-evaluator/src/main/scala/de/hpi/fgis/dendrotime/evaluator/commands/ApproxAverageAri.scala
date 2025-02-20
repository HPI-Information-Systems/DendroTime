package de.hpi.fgis.dendrotime.evaluator.commands


import caseapp.{Command, Recurse, RemainingArgs}
import de.hpi.fgis.dendrotime.evaluator.{CommonArguments, Evaluator}

case class ApproxAverageAriOptions(@Recurse common: CommonArguments, factor: Double = 1.3)

object ApproxAverageAri extends Command[ApproxAverageAriOptions] {

  override val name = "approxAverageAri"

  def run(options: ApproxAverageAriOptions, args: RemainingArgs): Unit =
    Evaluator(options.common).approxAverageAri(options.factor)
}
