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

  override def persistenceId = "AccountReporteR"


  var balanceCache = 0f
  var journalEventOffset= 0L

  def bookKeeping(): Unit ={
    journalEventOffset+=1
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
    println(s"after operation, cache: $balanceCache , offset: $journalEventOffset")
  }

  val receiveRecover: Receive = {

    case evt: DomainEvent =>
      updateCache(evt)

    case SnapshotOffer(_, ReporterCache(cache_, offset_)) =>
      balanceCache= cache_
      journalEventOffset= offset_
  }

  val receiveCommand: Receive = {

    case `requestSnapshot`⇒
      saveSnapshot(
        ReporterCache(balanceCache, journalEventOffset)
      )

    case `LetItFlow` ⇒
      val queries = PersistenceQuery(context.system).
        readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
      lazy val events = queries.eventsByPersistenceId(Account.persistenceId,journalEventOffset)
      implicit val materializer = ActorMaterializer()
      events.runWith(Sink.actorRef(self, None))

    case EventEnvelope(_,_,_,evt:DomainEvent) ⇒
      updateCache(evt)

    case EventEnvelope(_,_,_,_) ⇒ bookKeeping()

    case evt ⇒ println(s"didn't expect: $evt")
  }
}

object ReporterActor {
  def props() = Props(new ReporterActor(2))
}
