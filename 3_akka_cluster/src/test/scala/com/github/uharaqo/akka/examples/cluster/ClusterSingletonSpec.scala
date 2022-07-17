package com.github.uharaqo.akka.examples.cluster

import akka.actor.testkit.typed.scaladsl.{ ActorTestKit, ScalaTestWithActorTestKit }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior, SupervisorStrategy }
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
import scala.language.postfixOps

class ClusterSingletonSpec extends AnyWordSpecLike with BeforeAndAfterAll with Matchers {

  private val testKit1 = newTestKit("TestClusterSystem", 15003)
  private val testKit2 = newTestKit("TestClusterSystem", 15004)

  override def afterAll(): Unit = Seq(testKit1, testKit2).foreach(_.shutdownTestKit())

  private val queue = new LinkedBlockingDeque[String]()

  private def newTester(): Behavior[String] =
    Behaviors
      .supervise(Behaviors.receive[String] { (ctx, msg) =>
        queue.add(s"${ctx.self.path}: $msg")
        println(s"${ctx.self.path}: $msg")
        Behaviors.same
      })
      .onFailure(SupervisorStrategy.restart)

  "cluster" must {
    "singleton" in {
      val cluster1          = Cluster(testKit1.system)
      val singletonManager1 = ClusterSingleton(testKit1.system)
      val singleton1        = singletonManager1.init(SingletonActor(newTester(), "Singleton1"))

      val cluster2          = Cluster(testKit2.system)
      val singletonManager2 = ClusterSingleton(testKit2.system)
      val singleton2        = singletonManager2.init(SingletonActor(newTester(), "Singleton1"))

      cluster1.manager ! JoinSeedNodes(Seq(cluster1.selfMember.address, cluster2.selfMember.address))
      cluster2.manager ! JoinSeedNodes(Seq(cluster1.selfMember.address, cluster2.selfMember.address))

      Thread.sleep(10000)

      singleton1 ! "1st"
      singleton2 ! "2nd"

      Thread.sleep(1000)

      val messages = queue.toArray
      messages should contain allElementsOf Seq(
        "akka://TestClusterSystem/system/singletonManagerSingleton1/Singleton1: 1st",
        "akka://TestClusterSystem/system/singletonManagerSingleton1/Singleton1: 2nd",
      )
      messages should have size 2
    }
  }

  private def newTestKit(name: String, port: Int) =
    ActorTestKit(
      name,
      ConfigFactory
        .parseString(
          s"""
          akka.remote.artery.canonical.port=$port
          akka.cluster.min-nr-of-members = 2
          """
        )
        .withFallback(ConfigFactory.load())
    )
}
