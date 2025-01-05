package de.hpi.fgis.dendrotime.runner

import caseapp.*

@AppName("DendroTime Runner")
@ProgName("runner")
case class Arguments(
                      dataset: String,
                      serial: Boolean = false,
                      metric: String = "msm",
                      linkage: String = "ward",
                      strategy: String = "approx-distance-ascending",
                    )

object Arguments {
  given Parser[Arguments] = Parser.derive
  given Help[Arguments] = Help.derive
}
