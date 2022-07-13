package com.github.uharaqo.akka.examples.persistence

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.SerializationSettings
import com.typesafe.config.ConfigFactory
import com.typesafe.sslconfig.util.ConfigLoader
//import akka.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.github.uharaqo.akka.examples.persistence.es.TaskManagerActor
import com.github.uharaqo.akka.examples.persistence.es.TaskManagerCommand.*
import com.github.uharaqo.akka.examples.persistence.es.TaskManagerEvent.*
import com.github.uharaqo.akka.examples.persistence.es.TaskManagerState.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

class TaskManagerSpec
    extends ScalaTestWithActorTestKit(
      EventSourcedBehaviorTestKit.config
        .withFallback(ConfigFactory.load("application.conf"))
//        .withFallback(EventSourcedBehaviorTestKit.config)
    )
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with LogCapturing {

  private val taskId1 = "task1"
  private val taskId2 = "task2"
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[Command, Event, Task](
      system,
      TaskManagerActor(PersistenceId.ofUniqueId("root")),
      SerializationSettings.disabled
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "TaskManager" must {
    "emits events and states properly" in {
      // util function to verify a result
      def verify(
          result: EventSourcedBehaviorTestKit.CommandResult[Command, Event, Task],
          state: Task,
          event: Option[Event],
      ): Unit = {
        result.stateOfType[Task] shouldBe state
        event.fold(() => result.hasNoEvents shouldBe true)(result.event shouldBe _)
      }

      // empty
      val getResult1 = eventSourcedTestKit.runCommand[StatusReply[Task]](GetTask.apply)
      getResult1.reply.isError shouldBe true
      getResult1.reply.getError.getMessage shouldBe "Not Found"

      // start a task
      val started = eventSourcedTestKit.runCommand(StartTask(taskId1))
      val task    = Task(Some(taskId1))
      verify(started, task, Some(TaskStarted(taskId1)))

      // GetCommand works
      val getResult2 = eventSourcedTestKit.runCommand[StatusReply[Task]](GetTask.apply)
      getResult2.reply.getValue shouldBe task
      verify(getResult2, task, None)

      // a duplicated command gets ignored
      val duplicate = eventSourcedTestKit.runCommand(StartTask(taskId1))
      verify(duplicate, task, None)

      val next = eventSourcedTestKit.runCommand(NextStep(taskId1, "instruction1"))
      verify(next, task, Some(TaskStep(taskId1, "instruction1")))

      // commands for taskId2 get stacked
      val stashed1 = eventSourcedTestKit.runCommand(StartTask(taskId2))
      verify(stashed1, task, None) // same result

      val stashed2 = eventSourcedTestKit.runCommand(NextStep(taskId2, "instruction2"))
      verify(stashed2, task, None) // same result

      // task1 completed. stashed commands are processed
      val completed1 = eventSourcedTestKit.runCommand(EndTask(taskId1))
      verify(completed1, Task(Some(taskId2)), Some(TaskCompleted(taskId1)))

      // no more task
      val completed2 = eventSourcedTestKit.runCommand(EndTask(taskId2))
      verify(completed2, Task(None), Some(TaskCompleted(taskId2)))
    }
  }
}
