val scala3Version = "3.1.3"
//val scala2Version = "2.13.8"

val AppVersion                 = "0.1.0"
val AkkaVersion                = "2.6.19"
val AkkaHttpVersion            = "10.2.9"
val AkkaManagementVersion      = "1.1.3"
val AkkaPersistenceJdbcVersion = "5.0.4"
val AlpakkaKafkaVersion        = "3.0.0"
val AkkaProjectionVersion      = "1.2.4"
val ScalikeJdbcVersion         = "4.0.0"

val akkaCommon = Seq(
  // akka
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-slf4j"       % AkkaVersion,
  // util
  "org.scalactic" %% "scalactic"       % "3.2.12",
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  // test
  "org.scalatest"     %% "scalatest"                % "3.2.12"    % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
)

val baseSettings = Seq(
  organization := "com.github.uharaqo",
  scalaVersion := scala3Version,
  version := AppVersion,
  libraryDependencies ++= akkaCommon,
  Test / parallelExecution := false,
  run / fork := false,
  Global / cancelable := false // ctrl-c
)

val persistenceSettings = Seq(
  // akka
  "com.typesafe.akka" %% "akka-persistence-typed"     % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  // io
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  // test
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
)

ThisBuild / scalaVersion := scala3Version

lazy val root = project
  .in(file("."))
  .aggregate(basics, persistenceBasics)

lazy val basics =
  (project in file("1_akka_basics"))
    .settings(baseSettings)
    .settings(
      name := "akka_basics",
      libraryDependencies ++= Seq()
    )

lazy val persistenceBasics =
  (project in file("4_akka_persistence_basics"))
    .settings(baseSettings)
    .settings(
      name := "akka_persistence_basics",
      libraryDependencies ++= persistenceSettings
    )
