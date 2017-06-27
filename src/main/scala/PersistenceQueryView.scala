package com.packt.akka

import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.reflect.ClassTag

abstract class PersistenceQueryView[DomainEvent, Data]
(implicit domainEvent: ClassTag[DomainEvent]) extends PersistentActor{

  case class QueriedCachedData(cache:Data, offset:Long)
  case object RequestSnapshot
  case object StartQueryStream

  val snapshotFrequency:Int

  val persistenceIDtoQuery:String

  val journalIdentifier: String

  var cachedData :Data
  var journalEventOffset: Long = 0L

  def bookKeeping(): Unit ={
    journalEventOffset+=1
    if (journalEventOffset % snapshotFrequency == 0)
      self ! RequestSnapshot
  }

  def queryJournal(offset: Long):Option[Source[EventEnvelope, Unit]] = {

    val pf: PartialFunction[String, Source[EventEnvelope, Unit]] = {
      case id@LeveldbReadJournal.Identifier ⇒
        PersistenceQuery(context.system).
          readJournalFor[LeveldbReadJournal](id).
          eventsByPersistenceId(persistenceIDtoQuery,offset)
    }
    pf.lift(journalIdentifier)
  }

  val receiveRecover: Receive = {
    case SnapshotOffer(_, QueriedCachedData(cache_, offset_)) =>
      cachedData = cache_
      journalEventOffset= offset_

    case RecoveryCompleted =>
      self ! StartQueryStream
  }

  val receiveReadCommand: Receive

  val receiveQueryViewCommand: Receive = {

    case `RequestSnapshot`⇒
      saveSnapshot(
        QueriedCachedData(cachedData, journalEventOffset)
      )

    case StartQueryStream ⇒

      implicit val materializer = ActorMaterializer()
      for{
        events ← queryJournal(journalEventOffset)
      } yield events.runWith(Sink.actorRef(self, None))

    case EventEnvelope(_,_,_,domainEvent(evt)) ⇒
      updateCache(evt)

    //internal events such as FSM state change which is private
    case EventEnvelope(_,_,_,_) ⇒ bookKeeping()

    case evt ⇒ println(s"didn't expect: $evt")
  }

  val receiveCommand: Receive = receiveQueryViewCommand orElse receiveReadCommand

  def updateCache(evt: DomainEvent): Unit

}
