package com.github.uharaqo.akka.examples.cluster

import akka.actor.*
import akka.actor.testkit.typed.scaladsl.{ ActorTestKit, ScalaTestWithActorTestKit }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior, SupervisorStrategy }
import akka.cluster.ClusterEvent.*
import akka.cluster.Member
import akka.cluster.MemberStatus.*
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityRef, EntityTypeKey }
import akka.cluster.typed.*
import akka.testkit.TestActor.Message
import akka.testkit.{ CallingThreadDispatcher, TestActor, TestKit }
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.*

import java.util.concurrent.LinkedBlockingDeque
import scala.collection.mutable

class ClusterShardingSpec extends AnyWordSpecLike with BeforeAndAfterAll with Matchers {

  private val testKit1 = newTestKit("TestClusterSystem", 15005)
  private val testKit2 = newTestKit("TestClusterSystem", 15006)

  private val queue = new LinkedBlockingDeque[String]()

  private def newTester(entityId: String): Behavior[String] =
    Behaviors
      .supervise(Behaviors.receive[String] { (ctx, msg) =>
        queue.add(s"${ctx.self.path}: $entityId, $msg")
        println(s"${ctx.self.path}: $entityId, $msg")
        Behaviors.same
      })
      .onFailure(SupervisorStrategy.restart)

  override def afterAll(): Unit = Seq(testKit1, testKit2).foreach(_.shutdownTestKit())

  "cluster" must {
    "sharding works" in {
      val cluster1 = Cluster(testKit1.system)
      val cluster2 = Cluster(testKit2.system)

      cluster1.manager ! JoinSeedNodes(Seq(cluster1.selfMember.address, cluster2.selfMember.address))
      cluster2.manager ! JoinSeedNodes(Seq(cluster1.selfMember.address, cluster2.selfMember.address))

      val sharding1 = ClusterSharding(testKit1.system)
      val sharding2 = ClusterSharding(testKit2.system)

      val TypeKey      = EntityTypeKey[String]("Tester")
      val shardRegion1 = sharding1.init(Entity(TypeKey)(ctx => newTester(ctx.entityId)))
      val shardRegion2 = sharding2.init(Entity(TypeKey)(ctx => newTester(ctx.entityId)))

      Thread.sleep(10000)

      Seq("KeyA" -> "ValA", "KeyB" -> "ValB").foreach { e1 =>
        Seq(sharding1 -> shardRegion1, sharding2 -> shardRegion2).foreach { e2 =>
          e2._1.entityRefFor(TypeKey, e1._1) ! e1._2
          Thread.sleep(100)
          e2._2 ! ShardingEnvelope(e1._1, e1._2)
          Thread.sleep(100)
        }
      }

      Thread.sleep(1000)

      val events = queue.toArray
      events should contain allElementsOf Seq(
        // via sharding1
        "akka://TestClusterSystem/system/sharding/Tester/202/KeyA: KeyA, ValA",
        // via shardRegion1
        "akka://TestClusterSystem/system/sharding/Tester/202/KeyA: KeyA, ValA",
        // via sharding2
        "akka://TestClusterSystem/system/sharding/Tester/202/KeyA: KeyA, ValA",
        // via shardRegion2
        "akka://TestClusterSystem/system/sharding/Tester/202/KeyA: KeyA, ValA",

        // via sharding1
        "akka://TestClusterSystem/system/sharding/Tester/203/KeyB: KeyB, ValB",
        // via shardRegion1
        "akka://TestClusterSystem/system/sharding/Tester/203/KeyB: KeyB, ValB",
        // via sharding2
        "akka://TestClusterSystem/system/sharding/Tester/203/KeyB: KeyB, ValB",
        // via shardRegion2
        "akka://TestClusterSystem/system/sharding/Tester/203/KeyB: KeyB, ValB",
      )
      events should have size 8
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
