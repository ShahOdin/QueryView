package com.packt.akka

import akka.actor.Props
import akka.persistence._
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.packt.akka.Account.{DomainEvent, _}

case class ReporterCache(cache:Float, offset:Long)
case object requestSnapshot
case object LetItFlow

class ReporterActor(snapshotFrequency:Int) extends PersistentActor {

  override def persistenceId = "accountReporter8"

  //events.map(elem => self ! elem).runWith(Sink.ignore)
  //events.runWith(Sink.actorRef(self, None))

  var balanceCache = 0f
  var journalEventOffset= 0L

  def bookKeeping()={
    journalEventOffset+=1
    println(s"after operation, cache: $balanceCache , offset: $journalEventOffset")
    if (journalEventOffset % snapshotFrequency == 0)
      self ! requestSnapshot
  }

  def updateCache(evt: DomainEvent): Unit = {
    evt match {
      case AcceptedTransaction(amount, CR) ⇒
        balanceCache += amount
      case AcceptedTransaction(amount, DR) ⇒
        val newAmount = balanceCache - amount
        if (newAmount > 0)
          balanceCache = newAmount
      case RejectedTransaction(_, _, _) ⇒ //nothing
    }
    bookKeeping()
  }

  val receiveRecover: Receive = {

    case evt: DomainEvent =>
      println(s"Reporter received $evt on recovering mode")
      updateCache(evt)

    case SnapshotOffer(_, ReporterCache(cache_, offset_)) =>
      println(s"Counter receive snapshot with data: cache: $cache_ and offset: $offset_ on recovering mood")
      balanceCache= cache_
      journalEventOffset= offset_

    case RecoveryCompleted =>
      println(s"Recovery Complete and Now the reporter Class will switch to receiving mode")
  }

  val receiveCommand: Receive = {
    case SaveSnapshotSuccess(_) =>
      println(s"save snapshot succeed, with data: cache: $balanceCache and offset: $journalEventOffset ")
    case SaveSnapshotFailure(_, reason) =>
      println(s"save snapshot failed and failure is $reason")

    case `requestSnapshot`⇒
      saveSnapshot(
        ReporterCache(balanceCache, journalEventOffset)
      )

    case `LetItFlow` ⇒

      println(s"Let the stream take us all: cache: $balanceCache and offset: $journalEventOffset")
      val queries = PersistenceQuery(context.system).
        readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
      lazy val events = queries.eventsByPersistenceId(Account.persistenceId,journalEventOffset)
      implicit val materializer = ActorMaterializer()
      //events.map(elem => self ! elem).runWith(Sink.ignore)
      events.runWith(Sink.actorRef(self, None))

    //the singleton's events
    case EventEnvelope(offset,_,_,evt:DomainEvent) ⇒
      println(s"Reporter received the account's command: $evt with offset: $offset")
      persist(evt) { evt =>
        updateCache(evt)
      }
    //internal events
    case EventEnvelope(_,_,_,_) ⇒
      //bookKeeping()

    case evt ⇒ println(s"didn't: $evt")
  }
}

object ReporterActor {
  def props() = Props(new ReporterActor(10))
}
