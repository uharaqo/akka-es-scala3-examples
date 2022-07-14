package com.github.uharaqo.akka.examples.persistence.es.taskmanager

object TaskManagerState {

  final case class Task(taskIdInProgress: Option[String])
}
