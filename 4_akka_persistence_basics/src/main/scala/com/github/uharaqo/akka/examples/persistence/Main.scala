package com.github.uharaqo.akka.examples.persistence

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import com.github.uharaqo.akka.examples.persistence.es.TaskManagerActor
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object Main {
  private val logger = LoggerFactory.getLogger(getClass)

  def apply[T](init: () => ActorSystem[T]): Unit = {
    logger.info("Starting Akka")

    val system = init()

    try logger.info("Started Akka")
    catch {
      case e: Throwable =>
        logger.error("Terminating Akka due to initialization failure", e)
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit =
    Main(() => ActorSystem(TaskManagerActor(PersistenceId.ofUniqueId("test1")), "TaskManagerService"))
}
