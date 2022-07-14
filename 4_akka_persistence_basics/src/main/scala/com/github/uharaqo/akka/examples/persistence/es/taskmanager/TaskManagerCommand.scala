package com.github.uharaqo.akka.examples.persistence.es.taskmanager

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.*
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.{ DeserializationContext, JsonDeserializer }
import com.github.uharaqo.akka.examples.persistence.es.taskmanager.TaskManagerCommand.GetTask
import com.github.uharaqo.akka.examples.persistence.es.taskmanager.TaskManagerEvent.*
import com.github.uharaqo.akka.examples.persistence.es.taskmanager.TaskManagerState.*

object TaskManagerCommand {

  sealed trait Command
  final case class GetTask(replyTo: ActorRef[StatusReply[Task]]) extends Command
  final case class StartTask(taskId: String)                     extends Command
  final case class NextStep(taskId: String, instruction: String) extends Command
  final case class EndTask(taskId: String)                       extends Command

  val commandHandler: (Task, Command) => Effect[Event, Task] = { (state, command) =>
    state.taskIdInProgress match {
      case None =>
        command match {
          case GetTask(replyTo) =>
            Effect.reply(replyTo)(StatusReply.error("Not Found"))

          case StartTask(taskId) =>
            Effect.persist(TaskStarted(taskId))

          case _ =>
            Effect.unhandled
        }

      case Some(inProgress) =>
        command match {
          case GetTask(replyTo) =>
            Effect.reply(replyTo)(StatusReply.success(state))

          case StartTask(taskId) =>
            if (inProgress == taskId)
              Effect.none // duplicate, already in progress
            else
              // other task in progress, wait with new task until later
              Effect.stash()

          case NextStep(taskId, instruction) =>
            if (inProgress == taskId)
              Effect.persist(TaskStep(taskId, instruction))
            else
              // other task in progress, wait with new task until later
              Effect.stash()

          case EndTask(taskId) =>
            if (inProgress == taskId)
              Effect.persist(TaskCompleted(taskId)).thenUnstashAll() // continue with next task
            else
              // other task in progress, wait with new task until later
              Effect.stash()
        }
    }
  }
}
