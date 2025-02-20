package de.hpi.fgis.dendrotime.evaluator

import caseapp.*

@AppName("DendroTime Evaluator")
@ProgName("evaluator")
case class Arguments(
                      predHierarchyPath: String,
                      targetHierarchyPath: String,
                    )

object Arguments {
  given Parser[Arguments] = Parser.derive

  given Help[Arguments] = Help.derive
}
