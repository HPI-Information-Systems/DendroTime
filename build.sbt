import sbt.Keys.mainClass
import sbtassembly.Assembly.{Library, Project}
import sbtassembly.CustomMergeStrategy

import scala.sys.process.*

lazy val akkaVersion = "2.9.3"
lazy val akkaHttpVersion = "10.6.3"
lazy val targetScalaVersion = "3.3.3"
lazy val scalaTestVersion = "3.2.19"

ThisBuild / organization := "de.hpi.fgis"
//ThisBuild / version := "0.1.0-SNAPSHOT" // automatically filled by plugin
ThisBuild / scalaVersion := targetScalaVersion

ThisBuild / resolvers += "Akka library repository".at("https://repo.akka.io/maven")

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
ThisBuild / fork := true
ThisBuild / Test / logBuffered := false

// enable test coverage collection (instruments compiled code: just turn on if coverage is desired!)
//ThisBuild / coverageEnabled := true

ThisBuild / assembly / test := {}

lazy val `DendroTime` = project.in(file("."))
  .dependsOn(`backend`, `frontend`)
  .settings(
    name := "DendroTime",
    Compile / mainClass := Some("de.hpi.fgis.dendrotime.DendroTimeServer")
  )

lazy val `runner` = project.in(file("dendrotime-runner"))
  .dependsOn(`backend`)
  .settings(
    name := "DendroTime-Runner",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.github.alexarchambault" %% "case-app" % "2.1.0-M29",
    ),
    Compile / mainClass := Some("de.hpi.fgis.dendrotime.runner.App"),
    assembly / mainClass := Some("de.hpi.fgis.dendrotime.runner.App"),
//    assembly / assemblyJarName := "DendroTime-runner.jar",
    assembly / assemblyOutputPath := file("experiments/DendroTime-runner.jar"),
    run / baseDirectory := file(".")
  )

lazy val `evaluator` = project.in(file("dendrotime-evaluator"))
  .dependsOn(`dendrotime-clustering`, `dendrotime-io`, `bloom-filter`)
  .settings(
    name := "DendroTime-Evaluator",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "case-app" % "2.1.0-M29",
    ),
    Compile / mainClass := Some("de.hpi.fgis.dendrotime.evaluator.App"),
    assembly / mainClass := Some("de.hpi.fgis.dendrotime.evaluator.App"),
    assembly / assemblyOutputPath := file("experiments/DendroTime-Evaluator.jar"),
    run / baseDirectory := file(".")
  )

lazy val `benchmarking` = project.in(file("dendrotime-benchmarking"))
  .dependsOn(`backend`)
  .enablePlugins(JmhPlugin)

lazy val `backend` = project.in(file("dendrotime-backend"))
  .dependsOn(`dendrotime-clustering` % "compile->compile;test->test", `dendrotime-io`)
  .settings(
    name := "DendroTime-Server",
    libraryDependencies ++= Seq(
      // akka
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      // math stuff
      "org.apache.commons" % "commons-math3" % "3.6.1",

      // logging
      "ch.qos.logback" % "logback-classic" % "1.5.16",

      // test
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test
    ),
    javacOptions += "-Xlint:deprecation",
    javaOptions ++= Seq("-Xmx2G", "-Dfile.encoding=UTF-8"),
    Compile / mainClass := Some("de.hpi.fgis.dendrotime.DendroTimeServer"),
  )

lazy val `frontend` = project.in(file("dendrotime-frontend"))
  .settings(
    name := "DendroTime-UI",
    Compile / resourceGenerators += buildFrontend.init
  )

lazy val buildFrontend = taskKey[Seq[File]]("Generate UI resources") := {
  // build frontend
  val exitCode = Process("npm run build", baseDirectory.value).!
  if (exitCode != 0)
    throw new RuntimeException("Failed to build frontend")

  // copy frontend resources to target folder
  val webapp = baseDirectory.value / "dist"
  val managed = resourceManaged.value
  for {
    (from, to) <- webapp ** "*" pair Path.rebase(webapp, managed / "main" / "frontend")
  } yield {
    Sync.copy(from, to)
    to
  }
}

lazy val `dendrotime-clustering` = project.in(file("dendrotime-clustering"))
  .dependsOn(`bloom-filter`, `dendrotime-io`)
  .settings(
    name := "dendrotime.clustering",
    libraryDependencies ++= Seq(
      // fft
      "net.java.dev.jna" % "jna" % "5.16.0",
      // math stuff
      "org.apache.commons" % "commons-math3" % "3.6.1",
      // test
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
      "org.scalacheck" %% "scalacheck" % "1.18.1" % Test
    ),
  )

lazy val `dendrotime-io` = project.in(file("dendrotime-io"))
  .dependsOn(`bloom-filter`)
  .settings(
    name := "dendrotime.io",
    libraryDependencies ++= Seq(
      // csv parsing
      "com.univocity" % "univocity-parsers" % "2.9.1",
      // test
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
      "org.scalacheck" %% "scalacheck" % "1.18.1" % Test
    ),
  )

lazy val `bloom-filter` = project.in(file("bloom-filter"))
  .settings(
    name := "bloom-filter",
    version := "0.14.1",
    libraryDependencies ++= Seq(
      // math stuff
      "org.apache.commons" % "commons-math3" % "3.6.1",
      // test
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
      "org.scalacheck" %% "scalacheck" % "1.18.1" % Test
    ),
    scalacOptions ++= Seq(
      "-rewrite",
      "-deprecation",
      "-source:future-migration",
      "-explaintypes",
      "-feature",
      "-Xtarget:12",
      "-language:postfixOps",
    ),
    javacOptions += "-Xlint:deprecation",
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

lazy val `progress-bar` = project.in(file("progress-bar"))
  .settings(
    name := "progress-bar",
    version := "0.1.0",
  )

// merge strategy for the assembly plugin
ThisBuild / assembly / assemblyMergeStrategy := {
  // discard JDK11+ module infos from libs (not required for assembly)
  case "module-info.class" => MergeStrategy.discard
  // discard logging configuration (set during deployment)
  case PathList("logback.xml") => MergeStrategy.discard
  // rename application.conf to reference.conf to allow partial overwrites for local configs
  case PathList("application.conf") => CustomMergeStrategy.rename{
    case Project(_, _, target, stream) =>
      target.replace("application.conf", "reference.conf")
    case l: Library => l.target
  }
  case "rootdoc.txt" => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
