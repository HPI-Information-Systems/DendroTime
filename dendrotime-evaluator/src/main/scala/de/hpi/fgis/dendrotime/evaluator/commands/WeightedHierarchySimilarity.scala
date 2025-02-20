package de.hpi.fgis.dendrotime.evaluator.commands


import caseapp.{Command, Recurse, RemainingArgs}
import de.hpi.fgis.dendrotime.evaluator.{CommonArguments, Evaluator}

case class WeightedHierarchySimilarityOptions(@Recurse common: CommonArguments, useBloomFilters: Boolean = true)

object WeightedHierarchySimilarity extends Command[WeightedHierarchySimilarityOptions] {

  override val name = "weightedHierarchySimilarity"

  def run(options: WeightedHierarchySimilarityOptions, args: RemainingArgs): Unit =
    Evaluator(options.common).weightedHierarchySimilarity(options.useBloomFilters)
}
