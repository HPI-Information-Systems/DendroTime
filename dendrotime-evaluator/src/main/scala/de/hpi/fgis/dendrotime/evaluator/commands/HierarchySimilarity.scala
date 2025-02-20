package de.hpi.fgis.dendrotime.evaluator.commands


import caseapp.{Command, Recurse, RemainingArgs}
import de.hpi.fgis.dendrotime.evaluator.{CommonArguments, Evaluator}

case class HierarchySimilarityOptions(
                                       @Recurse common: CommonArguments,
                                       useBloomFilters: Boolean = true,
                                       cardinalityLowerBound: Int = 3,
                                       cardinalityUpperBound: Int = 1
                                     )

object HierarchySimilarity extends Command[HierarchySimilarityOptions] {

  override val name = "hierarchySimilarity"

  def run(options: HierarchySimilarityOptions, args: RemainingArgs): Unit =
    Evaluator(options.common).hierarchySimilarity(
      options.useBloomFilters, options.cardinalityLowerBound, options.cardinalityUpperBound
    )
}
