package com.shah.persistence.demo

import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.actor.{Props, ActorRef, ActorSystem}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpecLike}

import scala.concurrent.duration._
import akka.pattern.ask

class AccountViewSpec extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterEach with ScalaFutures {

  def getAccountReader(): ActorRef = {
    import scala.concurrent.ExecutionContext.Implicits.global
    import com.shah.persistence.demo.TestUtils.Mock
    system.actorOf(Mock.AccountView.props(7))
  }

  def getAccountWriter(): ActorRef = {
    import com.shah.persistence.demo.account.Account
    system.actorOf(Props[Account])
  }

  def creditAccount(account: ActorRef, amount: Float): Unit ={
    account ! AccountApi.Operation(amount, AccountApi.CR)
  }

  def debitAccount(account: ActorRef, amount: Float): Unit ={
    account ! AccountApi.Operation(amount, AccountApi.DR)
  }

  import scala.concurrent.Future
  def returnAccountBalance(reader: ActorRef): Future[Float] = {
    import akka.util.Timeout
    implicit val duration: Timeout = 5 seconds

    (reader ? AccountViewApi.ReturnAccountBalance).mapTo[Float]
  }

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

    import akka.persistence.query.EventEnvelope
    import akka.stream.scaladsl.Source
    import akka.NotUsed
    def assertEventsQueriedReceived(eventSource: Source[EventEnvelope, NotUsed],
                                    NumberOfAcceptedTransactions: Int) = {
      import akka.stream.ActorMaterializer
      implicit val materializer = ActorMaterializer()(system)
      eventSource.runFold {
        List[Any]()
      } {
        (x: List[Any], y: EventEnvelope) ⇒
          import com.shah.persistence.demo.account.Account.AcceptedTransaction
          y.event match {
            case AcceptedTransaction(_, _) ⇒ y.event :: x
            case _                         ⇒ x
          }
      }.futureValue.length shouldBe NumberOfAcceptedTransactions
    }

    import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
    def memoryReadJournal: InMemoryReadJournal = {
      import akka.persistence.query.PersistenceQuery
      PersistenceQuery(system).
        readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)
    }

    "should be using the correct memoryReadJournal" in {
      import com.shah.persistence.demo.account.Account

      val account = getAccountWriter()
      creditAccount(account, 4000)
      creditAccount(account, 8000)
      Thread.sleep(2000)

      val allEvents= memoryReadJournal.currentEventsByPersistenceId(Account.identifier, 0, Long.MaxValue)
      assertEventsQueriedReceived(allEvents, 2)

    }

    "receive existing journal events from the write side." in {

      val account = getAccountWriter()
      creditAccount(account, 1000)
      creditAccount(account, 3000)
      debitAccount(account, 500)

      val reader = getAccountReader()
      Thread.sleep(2000)
      val balance = returnAccountBalance(reader)
      balance.futureValue shouldBe 3500f
      killActors(reader, account)
    }

    "receive the messages being persisted by the write side after it is spawn." in {

      val account = getAccountWriter()
      creditAccount(account, 4000)

      val reader = getAccountReader()
      creditAccount(account, 3000)
      debitAccount(account, 500)
      Thread.sleep(2000)
      val balance = returnAccountBalance(reader)
      balance.futureValue shouldBe 6500f
      killActors(reader, account)
    }

    "snapshot its cache based on the write events." in {

      val account = getAccountWriter()
      val creditAttempts = 191
      val creditAmount = 1000
      for (i <- 1 to creditAttempts) {
        creditAccount(account, creditAmount)
      }

      val reader = getAccountReader()
      killActors(reader)
      Thread.sleep(4000)
      val resurrectedReader = getAccountReader()
      Thread.sleep(1000)
      val balance = returnAccountBalance(resurrectedReader)
      balance.futureValue shouldBe creditAmount * creditAttempts
      killActors(resurrectedReader, account)
    }

  }
}
