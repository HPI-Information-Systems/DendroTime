package de.hpi.fgis.dendrotime.evaluator.commands

import caseapp.{Command, Recurse, RemainingArgs}
import de.hpi.fgis.dendrotime.evaluator.{CommonArguments, Evaluator}

case class AriAtOptions(
                         @Recurse
                         common: CommonArguments,
                         k: Int = 10
                       )

object AriAt extends Command[AriAtOptions] {

  override val name = "ariAt"

  def run(options: AriAtOptions, args: RemainingArgs): Unit = Evaluator(options.common).ariAt(options.k)
}
