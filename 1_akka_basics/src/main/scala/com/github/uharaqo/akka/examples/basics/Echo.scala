package com.github.uharaqo.akka.examples.basics

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.util.Timeout
import com.github.uharaqo.akka.examples.basics.Echo

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object Echo {

  sealed trait Message

  case class Ping(message: String, replyTo: ActorRef[Pong]) extends Message

  case class Stop(replyTo: ActorRef[Boolean]) extends Message

  case class Pong(message: String)

  def apply(): Behavior[Message] =
    Behaviors.setup { ctx =>
      addShutdownHook(ctx.system)(ctx.executionContext)

      Behaviors.receiveMessage {
        case Ping(m, replyTo) =>
          replyTo ! Pong(m)
          Behaviors.same
        case Stop(replyTo) =>
          replyTo.tell(true)
          Behaviors.stopped
      }
    }

  def addShutdownHook(system: ActorSystem[_])(implicit ec: ExecutionContext): Unit = {
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "someTaskName") { () =>
      println("shutting down Echo...")

      implicit val timeout: Timeout = 5.seconds
      Future {
        Thread.sleep(1000)
        // cleanup()
        println("Echo shutdown complete...")
        Done
      }
    }
  }
}
