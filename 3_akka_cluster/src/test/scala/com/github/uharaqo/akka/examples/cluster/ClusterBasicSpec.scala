package com.github.uharaqo.akka.examples.cluster

import akka.actor.testkit.typed.scaladsl.{ ActorTestKit, ScalaTestWithActorTestKit }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.*
import akka.cluster.ClusterEvent.*
import akka.cluster.Member
import akka.cluster.MemberStatus.*
import akka.cluster.typed.*
import akka.testkit.TestActor.Message
import akka.testkit.{ CallingThreadDispatcher, TestActor, TestKit }
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.*

import java.util.concurrent.LinkedBlockingDeque
import scala.collection.mutable

class ClusterBasicSpec extends AnyWordSpecLike with BeforeAndAfterAll with Matchers {

  private val testKit1 = newTestKit("TestClusterSystem", 15001)
  private val testKit2 = newTestKit("TestClusterSystem", 15002)

  override def afterAll(): Unit = Seq(testKit1, testKit2).foreach(_.shutdownTestKit())

  private val queue = mutable.Queue[ClusterDomainEvent]()

  object Subscriber {
    def apply(): Behavior[ClusterDomainEvent] =
      Behaviors.receiveMessage { msg =>
        queue += msg
        println(msg)

        Behaviors.same
      }
  }

  "cluster" must {
    "join / leave emit events for subscription" in {
      val cluster1   = Cluster(testKit1.system)
      val subscriber = testKit1.spawn(Subscriber(), "subscriber")
      cluster1.subscriptions ! Subscribe(subscriber, classOf[MemberEvent])

      val cluster2 = Cluster(testKit2.system)
      cluster1.manager ! JoinSeedNodes(Seq(cluster1.selfMember.address, cluster2.selfMember.address))
      cluster2.manager ! JoinSeedNodes(Seq(cluster1.selfMember.address, cluster2.selfMember.address))

      Thread.sleep(100)

      val actor1 = testKit1.spawn(Behaviors.empty, "actor1")
      cluster1.manager ! Join(actor1.path.address)

      Thread.sleep(1000)

      val actor2 = testKit2.spawn(Behaviors.empty, "actor2")
      cluster2.manager ! Join(actor2.path.address)

      Thread.sleep(1000)

      cluster2.manager ! Leave(actor2.path.address)
      Thread.sleep(1000)

      cluster1.manager ! Leave(actor1.path.address)
      Thread.sleep(1000)

      val events = queue.toArray
      events should have size 9
      events.map(_.toString) should contain allElementsOf Seq(
        "MemberUp(Member(akka://TestClusterSystem@192.168.1.68:15001, Up))",
        "MemberJoined(Member(akka://TestClusterSystem@192.168.1.68:15002, Joining))",
        "MemberUp(Member(akka://TestClusterSystem@192.168.1.68:15002, Up))",
        "MemberLeft(Member(akka://TestClusterSystem@192.168.1.68:15002, Leaving))",
        "MemberExited(Member(akka://TestClusterSystem@192.168.1.68:15002, Exiting))",
        "MemberLeft(Member(akka://TestClusterSystem@192.168.1.68:15001, Leaving))",
        "MemberRemoved(Member(akka://TestClusterSystem@192.168.1.68:15002, Removed),Exiting)",
        "MemberExited(Member(akka://TestClusterSystem@192.168.1.68:15001, Exiting))",
        "MemberRemoved(Member(akka://TestClusterSystem@192.168.1.68:15001, Removed),Exiting)",
      )
    }
  }

  private def newTestKit(name: String, port: Int) =
    ActorTestKit(
      name,
      ConfigFactory
        .parseString(
          s"""
          akka.remote.artery.canonical.port=$port
          """
        )
        .withFallback(ConfigFactory.load())
    )
}
