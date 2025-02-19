package de.hpi.fgis.dendrotime.evaluator

import caseapp.{CaseApp, RemainingArgs}

object App extends CaseApp[Arguments] {

  def run(options: Arguments, remainingArgs: RemainingArgs): Unit = {
    println(s"${options.toString}, $remainingArgs")
  }
}
