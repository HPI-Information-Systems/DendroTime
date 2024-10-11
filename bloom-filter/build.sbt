import sbt.*
import sbt.TaskKey

lazy val testAll = TaskKey[Unit]("test-all")

lazy val root = (project in file("."))
  .settings(
    organization := "de.hpi.fgis",
    name := "bloom-filter",
    version := "0.14.0",
    scalaVersion := "3.3.3",
    autoCompilerPlugins := true,
    scalacOptions ++= scalacSettings,
    javacOptions += "-Xlint:deprecation",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.18" % "test",
      "org.scalacheck" %% "scalacheck" % "1.18.1" % "test"
    ),
    // testing settings
    Test / javaOptions += "-Xmx1G",
    Test / fork := true,
    Test / parallelExecution := false,
    Test / testOptions += Tests.Argument(
      TestFrameworks.ScalaCheck,
      "-verbosity",
      "2"
    )
  )
  .enablePlugins()

val scalacSettings = Seq(
  "-deprecation",
  "-rewrite",
  "-source:future-migration",
  "-explaintypes",
  "-feature",
  "-Xtarget:12",
  "-language:postfixOps",
)
