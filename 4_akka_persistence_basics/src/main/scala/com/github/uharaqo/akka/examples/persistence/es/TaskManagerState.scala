package com.github.uharaqo.akka.examples.persistence.es

object TaskManagerState {

  final case class Task(taskIdInProgress: Option[String])
}
