package de.hpi.fgis.dendrotime.evaluator

import caseapp.*


case class CommonArguments(
                            @Name("prediction")
                            predHierarchyPath: String,
                            @Name("target")
                            targetHierarchyPath: String,
                          )

object CommonArguments {
  given Parser[CommonArguments] = Parser.derive

  given Help[CommonArguments] = Help.derive
}
