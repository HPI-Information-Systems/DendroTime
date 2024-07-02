import sbt.Keys.mainClass
import scala.sys.process.*

lazy val akkaVersion = "2.9.3"
lazy val akkaHttpVersion = "10.6.3"
lazy val targetScalaVersion = "3.3.3"

ThisBuild / organization := "de.hpi.fgis"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := targetScalaVersion

ThisBuild / resolvers += "Akka library repository".at("https://repo.akka.io/maven")

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
ThisBuild / fork := true

lazy val `DendroTime` = project
  .in(file("."))
  .dependsOn(`frontend`)
  .settings(
    name := "DendroTime",
    libraryDependencies ++= Seq(
      // akka
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      // fft
      "net.java.dev.jna" % "jna" % "5.14.0",

      // logging
      "ch.qos.logback" % "logback-classic" % "1.5.6",

      // test
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    ),
    javacOptions += "-Xlint:deprecation",
  )

`DendroTime`/ Compile / mainClass := Some("de.hpi.fgis.dendrotime.DendroTimeServer")

lazy val `frontend` = project
  .in(file("./frontend"))
  .settings(
    Compile / resourceGenerators += buildFrontend.init
  )

lazy val buildFrontend = taskKey[Seq[File]]("Generate UI resources") := {
  // build frontend
  Process("npm run build", baseDirectory.value).!

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