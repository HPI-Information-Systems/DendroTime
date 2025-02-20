package de.hpi.fgis.dendrotime.evaluator.commands

import caseapp.{Command, Recurse, RemainingArgs}
import de.hpi.fgis.dendrotime.evaluator.{CommonArguments, Evaluator}

case class LabelChangesAtOptions(
                                  @Recurse
                                  common: CommonArguments,
                                  k: Option[Int] = None
                                )

object LabelChangesAt extends Command[LabelChangesAtOptions] {

  override val name = "labelChangesAt"

  def run(options: LabelChangesAtOptions, args: RemainingArgs): Unit =
    Evaluator(options.common).labelChangesAt(options.k)
}
