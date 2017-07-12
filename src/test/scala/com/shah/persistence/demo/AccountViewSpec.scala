package com.shah.persistence.demo

import org.scalatest._
import akka.actor.Props
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.pattern.ask
import org.scalatest.concurrent.ScalaFutures
import akka.actor.ActorSystem
import com.shah.persistence.demo.account.Account._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import com.shah.persistence.demo.TestUtils.Mock

class AccountViewSpec extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with ScalaFutures {

  import akka.actor.ActorRef
  import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
  import akka.persistence.query.PersistenceQuery
  import akka.util.Timeout

  implicit val duration: Timeout = 5 seconds

  override protected def beforeEach(): Unit = {
    import akka.persistence.inmemory.extension.{InMemoryJournalStorage, InMemorySnapshotStorage, StorageExtension}
    val tp = TestProbe()
    tp.send(StorageExtension(system).journalStorage, InMemoryJournalStorage.ClearJournal)
    tp.expectMsg(akka.actor.Status.Success(""))
    tp.send(StorageExtension(system).snapshotStorage, InMemorySnapshotStorage.ClearSnapshots)
    tp.expectMsg(akka.actor.Status.Success(""))
    super.beforeEach()
  }

  def killActors(actors: ActorRef*) = actors.foreach(system.stop)

  "AccountView" should {
    import akka.NotUsed
    import akka.persistence.query.EventEnvelope
    import akka.stream.scaladsl.Source

    "receive existing journal events from the write side." in {
      import com.shah.persistence.demo.account.Account
      import com.shah.persistence.demo.account.AccountViewApi._

      val account = system.actorOf(Props[Account])
      account ! Operation(1000, CR)
      account ! Operation(3000, CR)
      account ! Operation(500, DR)

      val reader = system.actorOf(Mock.AccountView.props(5))
      Thread.sleep(2000)
      val balance = (reader ? ReturnAccountBalance).mapTo[Float]
      balance.futureValue shouldBe 3500f
      killActors(reader, account)
    }

    "receive the messages being persisted by the write side after it is spawn." in {
      import com.shah.persistence.demo.account.Account
      import com.shah.persistence.demo.account.AccountViewApi.ReturnAccountBalance

      val account = system.actorOf(Props[Account])
      account ! Operation(4000, CR)

      val reader = system.actorOf(Mock.AccountView.props(5))
      account ! Operation(3000, CR)
      account ! Operation(500, DR)
      Thread.sleep(2000)
      val balance = (reader ? ReturnAccountBalance).mapTo[Float]
      balance.futureValue shouldBe 6500f
      killActors(reader, account)
    }

    "snapshot its cache based on the write events." in {
      import com.shah.persistence.demo.account.Account
      import com.shah.persistence.demo.account.AccountViewApi.ReturnAccountBalance

      val account = system.actorOf(Props[Account])
      for (i <- 1 to 10) {
        account ! Operation(4000, CR)
      }

      val reader = system.actorOf(Mock.AccountView.props(3))
      Thread.sleep(3000)
      system.stop(reader)

      val resurrectedReader = system.actorOf(Mock.AccountView.props(3))
      Thread.sleep(1000)

      val balance = resurrectedReader ? ReturnAccountBalance
      balance.futureValue shouldBe 40000f
      killActors(reader, account)
    }

    def assertEventsQueriedReceived(eventSource: Source[EventEnvelope, NotUsed],
                                    NumberOfAcceptedTransactions: Int) = {
      import akka.stream.ActorMaterializer
      implicit val materializer = ActorMaterializer()(system)
      eventSource.runFold {
        List[Any]()
      } {
        (x: List[Any], y: EventEnvelope) ⇒
          y.event match {
            case AcceptedTransaction(_, _) ⇒ y.event :: x
            case _                         ⇒ x
          }
      }.futureValue.length shouldBe NumberOfAcceptedTransactions
    }

    def allEventsSoFar(persistenceId: String) = PersistenceQuery(system).
      readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier).
      currentEventsByPersistenceId(persistenceId, Long.MinValue, Long.MaxValue)

    def allEventsSoFarAndBeyond(persistenceId: String) = PersistenceQuery(system).
      readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier).
      eventsByPersistenceId(persistenceId, Long.MinValue, Long.MaxValue)

    "'s write actor should have its events queriable with currentEventsByPersistenceId" in {
      import akka.NotUsed
      import akka.persistence.query.EventEnvelope
      import akka.stream.scaladsl.Source
      import com.shah.persistence.demo.account.Account

      val account = system.actorOf(Props[Account])
      account ! Operation(4000, CR)
      account ! Operation(4000, CR)
      Thread.sleep(2000)

      val allEvents: Source[EventEnvelope, NotUsed] = allEventsSoFar(Account.identifier)
      assertEventsQueriedReceived(allEvents, 2)

    }
    //fails.
    "'s write actor should have its events queriable with eventsByPersistenceId" in {
      import akka.NotUsed
      import akka.persistence.query.EventEnvelope
      import akka.stream.scaladsl.Source
      import com.shah.persistence.demo.account.Account

      val account = system.actorOf(Props[Account])
      account ! Operation(4000, CR)
      account ! Operation(4000, CR)
      Thread.sleep(2000)

      val allEvents: Source[EventEnvelope, NotUsed] = allEventsSoFarAndBeyond(Account.identifier)
      assertEventsQueriedReceived(allEvents, 2)

    }
  }
}
