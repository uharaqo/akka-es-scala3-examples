package com.github.uharaqo.akka.examples.persistence.es.taskmanager

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import com.github.uharaqo.akka.examples.persistence.es.taskmanager.TaskManagerState.*

object TaskManagerEvent {

  sealed trait Event
  final case class TaskStarted(taskId: String)                   extends Event
  final case class TaskStep(taskId: String, instruction: String) extends Event
  final case class TaskCompleted(taskId: String)                 extends Event

  def handleEvent(state: Task, event: Event): Task =
    event match {
      case TaskStarted(taskId) => Task(Option(taskId))
      case TaskStep(_, _)      => state
      case TaskCompleted(_)    => Task(None)
    }
}
