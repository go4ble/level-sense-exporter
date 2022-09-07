name := """level-sense-exporter"""
organization := "io.github.go4ble"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.8"

libraryDependencies ++= Seq(guice, ws, caffeine)
libraryDependencies ++= Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
  "io.prometheus" % "simpleclient" % "0.16.0",
  "io.prometheus" % "simpleclient_common" % "0.16.0",
  "io.prometheus" % "simpleclient_hotspot" % "0.16.0"
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "io.github.go4ble.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "io.github.go4ble.binders._"

dockerBaseImage := "openjdk:11"
dockerRepository := Some("go4ble")
dockerExposedPorts := Seq(9000)
dockerEnvVars := Map(
  "LSE_POLLING_PERIOD_MINUTES" -> "2",
  "LSE_USERNAME" -> "",
  "LSE_PASSWORD" -> ""
)
dockerUpdateLatest := true
javaOptions += "-Dpidfile.path=/dev/null"
