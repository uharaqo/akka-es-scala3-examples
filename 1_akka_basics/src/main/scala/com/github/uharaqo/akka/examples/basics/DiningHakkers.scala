package com.github.uharaqo.akka.examples.basics

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.github.uharaqo.akka.examples.basics.Chopstick.*
import com.github.uharaqo.akka.examples.basics.Hakker.Command

import scala.concurrent.duration.*

/**
 * Akka adaptation of [[http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/ the "Dining Philosophers" problem]]
 * to demonstrate a [[https://doc.akka.io/docs/akka/current/typed/fsm.html FSM]]
 */

// A Chopstick is an actor, it can be taken, and put back
object Chopstick {
  sealed trait ChopstickRequest

  final case class Take(ref: ActorRef[ChopstickResponse]) extends ChopstickRequest

  final case class Put(ref: ActorRef[ChopstickResponse]) extends ChopstickRequest

  sealed trait ChopstickResponse

  final case class Ok(chopstick: ActorRef[ChopstickRequest]) extends ChopstickResponse

  final case class Busy(chopstick: ActorRef[ChopstickRequest]) extends ChopstickResponse

  def apply(): Behavior[ChopstickRequest] = available()

  private def available(): Behavior[ChopstickRequest] = Behaviors.receivePartial {
    case (ctx, Take(hakker)) =>
      // let the hakker take this chopstick
      hakker ! Ok(ctx.self)
      takenBy(hakker)
  }

  private def takenBy(hakker: ActorRef[ChopstickResponse]): Behavior[ChopstickRequest] = Behaviors.receivePartial {
    case (ctx, Take(otherHakker)) =>
      // refuse the request because it's taken
      otherHakker ! Busy(ctx.self)
      Behaviors.same
    case (_, Put(`hakker`)) =>
      // the owner is putting this down
      available()
  }
}

// A hakker is an awesome dude or dudette who either thinks about hacking or has to eat ;-)
object Hakker {
  sealed trait Command

  case object Think extends Command

  case object Eat extends Command

  final case class HandleChopstickAnswer(msg: ChopstickResponse) extends Command

  def apply(name: String, left: ActorRef[ChopstickRequest], right: ActorRef[ChopstickRequest]): Behavior[Command] =
    Behaviors.setup { ctx =>
      new Hakker(ctx, name, left, right).waiting
    }
}

class Hakker(ctx: ActorContext[Command],
             name: String,
             left: ActorRef[ChopstickRequest],
             right: ActorRef[ChopstickRequest]) {

  import Hakker.*

  private val adapter = ctx.messageAdapter(res => HandleChopstickAnswer(res))

  def waiting: Behavior[Command] = Behaviors.receiveMessagePartial {
    case Think =>
      ctx.log.info("{} starts to think", name)
      startThinking(ctx, 5.seconds)
  }

  // When a hakker is thinking it can become hungry and try to pick up its chopsticks and eat
  private val thinking: Behavior[Command] = Behaviors.receiveMessagePartial {
    case Eat =>
      left ! Chopstick.Take(adapter)
      right ! Chopstick.Take(adapter)
      hungry
  }

  //When a hakker is hungry it tries to pick up its chopsticks and eat
  //When it picks one up, it goes into wait for the other
  //If the hakkers first attempt at grabbing a chopstick fails,
  //it starts to wait for the response of the other grab
  private lazy val hungry: Behavior[Command] = Behaviors.receiveMessagePartial {
    case HandleChopstickAnswer(Ok(`left`)) =>
      waitForOtherChopstick(chopstickToWaitFor = right, takenChopstick = left)

    case HandleChopstickAnswer(Ok(`right`)) =>
      waitForOtherChopstick(chopstickToWaitFor = left, takenChopstick = right)

    case HandleChopstickAnswer(Busy(_)) =>
      firstChopstickDenied
  }

  //When a hakker is waiting for the last chopstick it can either obtain it
  //and start eating, or the other chopstick was busy, and the hakker goes
  //back to think about how he should obtain his chopsticks :-)
  private def waitForOtherChopstick(chopstickToWaitFor: ActorRef[ChopstickRequest],
                                    takenChopstick: ActorRef[ChopstickRequest]
                                   ): Behavior[Command] = Behaviors.receiveMessagePartial {
    case HandleChopstickAnswer(Ok(`chopstickToWaitFor`)) =>
      ctx.log.info("{} has picked up {} and {} and starts to eat", name, left.path.name, right.path.name)
      startEating(ctx, 5.seconds)

    case HandleChopstickAnswer(Busy(`chopstickToWaitFor`)) =>
      takenChopstick ! Put(adapter)
      startThinking(ctx, 10.milliseconds)
  }

  //When a hakker is eating, he can decide to start to think,
  //then he puts down his chopsticks and starts to think
  private lazy val eating: Behavior[Command] = Behaviors.receiveMessagePartial {
    case Think =>
      ctx.log.info("{} puts down his chopsticks and starts to think", name)
      left ! Put(adapter)
      right ! Put(adapter)
      startThinking(ctx, 5.seconds)
  }

  //When the results of the other grab comes back,
  //he needs to put it back if he got the other one.
  //Then go back and think and try to grab the chopsticks again
  private lazy val firstChopstickDenied: Behavior[Command] = Behaviors.receiveMessagePartial {
    case HandleChopstickAnswer(Ok(chopstick)) =>
      chopstick ! Put(adapter)
      startThinking(ctx, 10.milliseconds)
    case HandleChopstickAnswer(Busy(_)) =>
      startThinking(ctx, 10.milliseconds)
  }

  // gets hungry after 5 sec
  private def startThinking(ctx: ActorContext[Command], duration: FiniteDuration) = Behaviors.withTimers[Command] { timers =>
    timers.startSingleTimer(key = Eat, msg = Eat, delay = duration)
    thinking
  }

  private def startEating(ctx: ActorContext[Command], duration: FiniteDuration) = Behaviors.withTimers[Command] { timers =>
    timers.startSingleTimer(key = Think, msg = Think, delay = duration)
    eating
  }
}

object DiningHakkers {

  def apply(): Behavior[NotUsed] = Behaviors.setup { ctx =>
    // Create 5 chopsticks
    val chopsticks =
      for (i <- 1 to 5) yield ctx.spawn(Chopstick(), "Chopstick" + i)

    // Create 5 awesome hakkers and assign them their left and right chopstick
    val hakkers = for {
      (name, i) <- List("Ghosh", "Boner", "Klang", "Krasser", "Manie").zipWithIndex
    } yield
      ctx.spawn(
        behavior = Hakker(
          name = name,
          left = chopsticks(i),
          right = chopsticks((i + 1) % 5)
        ),
        name = name
      )

    // Signal all hakkers that they should start thinking, and watch the show
    hakkers.foreach(_ ! Hakker.Think)

    Behaviors.empty
  }

  def main(args: Array[String]): Unit = {
    ActorSystem(DiningHakkers(), "DiningHakkers")
  }
}
