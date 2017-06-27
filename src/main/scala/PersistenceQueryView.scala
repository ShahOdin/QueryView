package com.packt.akka

import akka.persistence.query.EventEnvelope
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

  val persistenceIdtoQuery:String

  val journalQuerySupport:
    (String, Long) => Source[EventEnvelope, Unit]

  var cachedData :Data
  var journalEventOffset: Long = 0L

  def bookKeeping(): Unit ={
    journalEventOffset+=1
    if (journalEventOffset % snapshotFrequency == 0)
      self ! RequestSnapshot
  }

  val receiveRecover: Receive = {
    case SnapshotOffer(_, QueriedCachedData(cache_, offset_)) =>
      cachedData = cache_
      journalEventOffset = offset_

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
      val events= journalQuerySupport(persistenceIdtoQuery,journalEventOffset)
      events.runWith(Sink.actorRef(self, None))

    case EventEnvelope(_,_,_,domainEvent(evt)) ⇒
      updateCache(evt)

    //internal events such as FSM state change which is private
    case EventEnvelope(_,_,_,_) ⇒ bookKeeping()

    case evt ⇒ println(s"didn't expect: $evt")
  }

  val receiveCommand: Receive = receiveQueryViewCommand orElse receiveReadCommand

  def updateCache(evt: DomainEvent): Unit

}
