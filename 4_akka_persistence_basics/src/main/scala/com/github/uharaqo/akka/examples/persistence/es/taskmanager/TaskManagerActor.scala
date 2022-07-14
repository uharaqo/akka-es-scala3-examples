package com.github.uharaqo.akka.examples.persistence.es.taskmanager

import akka.actor.typed.*
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import com.github.uharaqo.akka.examples.persistence.es.taskmanager.TaskManagerCommand.*
import com.github.uharaqo.akka.examples.persistence.es.taskmanager.TaskManagerEvent.*
import com.github.uharaqo.akka.examples.persistence.es.taskmanager.TaskManagerState.*

import scala.concurrent.duration.DurationInt

object TaskManagerActor {
  private val eventHandler   = TaskManagerEvent.handleEvent
  private val commandHandler = TaskManagerCommand.commandHandler

  def apply(persistenceId: PersistenceId): EventSourcedBehavior[Command, Event, Task] =
    EventSourcedBehavior[Command, Event, Task](
      persistenceId = persistenceId,
      emptyState = Task(None),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(1.second, 30.seconds, 0.2))
}
