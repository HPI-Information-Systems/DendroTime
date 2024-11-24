package de.hpi.fgis.dendrotime.runner

import caseapp.*

@AppName("DendroTime Runner")
@ProgName("runner")
case class Arguments(
                      dataset: String,
                      metric: String = "msm",
                      linkage: String = "ward",
                      strategy: String = "fcfs",
                      approxLength: Int = 10,
                    )

object Arguments {
  given Parser[Arguments] = Parser.derive
  given Help[Arguments] = Help.derive
}
