package com.github.uharaqo.akka.examples.basics

import akka.actor.testkit.typed.scaladsl.*
import akka.actor.typed.scaladsl.Behaviors
import com.github.uharaqo.akka.examples.basics.Echo.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.*

import scala.concurrent.duration.DurationInt

class AsyncTestingExampleSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {

  private val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "echo" must {
    "call" in {
      val pinger = testKit.spawn(Echo(), "pinger")
      val probe  = testKit.createTestProbe[Pong]()
      pinger ! Ping("hello", probe.ref)
      probe.expectMessage(Pong("hello"))
    }

    "mock" in {
      val mockedBehavior = Behaviors.receiveMessagePartial[Message] { case Ping(message, replyTo) =>
        replyTo ! Pong(s"mock: ${message}")
        Behaviors.same
      }

      val pingProbe    = testKit.createTestProbe[Message]()
      val mockedPinger = testKit.spawn(Behaviors.monitor(pingProbe.ref, mockedBehavior))
      val pongProbe    = testKit.createTestProbe[Pong]()

      mockedPinger ! Ping("hello", pongProbe.ref)

      pongProbe.expectMessage(Pong("mock: hello"))
    }
  }
}
