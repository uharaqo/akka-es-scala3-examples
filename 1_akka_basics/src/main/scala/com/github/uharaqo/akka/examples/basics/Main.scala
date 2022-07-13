package com.github.uharaqo.akka.examples.basics

import akka.NotUsed
import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import com.github.uharaqo.akka.examples.basics.Echo

object Main {

  def apply(): Behavior[NotUsed] = Behaviors.setup { ctx =>
    val echo = ctx.spawn(Echo(), "Echo")

    val receiver = ctx.spawn(
      Behaviors.receiveMessage[Echo.Pong] { msg =>
        println(msg)
        ctx.system.terminate()
        Behaviors.stopped
      },
      "Receiver"
    )

    echo ! Echo.Ping("hello", receiver)

    Behaviors.empty
  }

  def main(args: Array[String]): Unit =
    ActorSystem(Main(), "Main")
}
