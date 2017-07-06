package com.shah.persistence.demo

import org.scalatest._
import akka.actor.Props
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.pattern.ask
import org.scalatest.concurrent.ScalaFutures
import akka.actor.ActorSystem
import com.shah.persistence.demo.Account.{CR, DR, Operation}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scalax.file.Path

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class AccountViewSpec extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with ScalaFutures {

  import akka.actor.ActorRef
  import akka.util.Timeout

  implicit val duration: Timeout = 5 seconds

  def deleteDirectory(pathS: String): Unit = {
    import scala.util.Try
    val path = Path.fromString(pathS)
    Try(path.deleteRecursively(continueOnFailure = false))
  }

  override def afterEach() = {
    deleteDirectory("target")
    Thread.sleep(2000)
  }

  def killActors(actors: ActorRef*) = actors.foreach(system.stop)

  "AccountView" should {

    "receive existing journal events from the write side." in {
      import com.shah.persistence.demo.AccountViewApi.ReturnAccountBalance

      val account = system.actorOf(Props[Account])
      account ! Operation(1000, CR)
      account ! Operation(3000, CR)
      account ! Operation(500, DR)

      val reader = system.actorOf(AccountView.props(5))
      Thread.sleep(2000)
      val balance = (reader ? ReturnAccountBalance).mapTo[Float]
      balance.futureValue shouldBe 3500f
      killActors(reader, account)
    }

    "receive the messages being persisted by the write side after it is spawn." in {
      import com.shah.persistence.demo.AccountViewApi.ReturnAccountBalance

      val account = system.actorOf(Props[Account])
      account ! Operation(4000, CR)

      val reader = system.actorOf(AccountView.props(5))
      account ! Operation(3000, CR)
      account ! Operation(500, DR)
      Thread.sleep(2000)
      val balance = (reader ? ReturnAccountBalance).mapTo[Float]
      balance.futureValue shouldBe 6500f
      killActors(reader, account)
    }

    "snapshot its cache based on the write events." in {
      import com.shah.persistence.demo.AccountViewApi.ReturnAccountBalance

      val account = system.actorOf(Props[Account])
      for (i <- 1 to 10) {
        account ! Operation(4000, CR)
      }

      val reader = system.actorOf(AccountView.props(3))
      Thread.sleep(3000)
      system.stop(reader)

      val resurrectedReader = system.actorOf(AccountView.props(3))
      Thread.sleep(1000)

      val balance = (resurrectedReader ? ReturnAccountBalance).mapTo[Float]
      balance.futureValue shouldBe 40000f
      killActors(reader, account)
    }

  }

}
