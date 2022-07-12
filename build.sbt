val scala2Version = "2.13.8"
val scala3Version = "3.1.3"

val AppVersion = "0.1.0"
val AkkaVersion = "2.6.19"
val AkkaHttpVersion = "10.2.9"
val AkkaManagementVersion = "1.1.3"
val AkkaPersistenceJdbcVersion = "5.0.4"
val AlpakkaKafkaVersion = "3.0.0"
val AkkaProjectionVersion = "1.2.4"
val ScalikeJdbcVersion = "4.0.0"

val akkaCommon = Seq(
  // akka
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  // test
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
).map(
  _.cross(CrossVersion.for3Use2_13)
) ++ Seq(
  "org.scalatest" %% "scalatest" % "3.2.12" % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.11",
)

val baseSettings = Seq(
  organization := "com.github.uharaqo",
  scalaVersion := scala3Version,
  // To cross compile with Scala 3 and Scala 2
  //  crossScalaVersions := Seq(scala3Version, scala2Version),
  libraryDependencies ++= akkaCommon,
  Test / parallelExecution := false,
  run / fork := false,
  Global / cancelable := false // ctrl-c
)

lazy val root = project.in(file("."))
  .aggregate(basics)

lazy val basics = (project in file("1_akka_basics"))
  .settings(baseSettings)
  .settings(
    name := "akka_basics",
    version := AppVersion,
    libraryDependencies ++= Seq(
    )
  )
