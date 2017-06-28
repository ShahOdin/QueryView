package com.shah.model

import akka.persistence.query.EventEnvelope
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.reflect.ClassTag

abstract class PersistenceQueryView[DomainEvent, SNData, Data<: Snapshottable[SNData]]
(implicit domainEvent: ClassTag[DomainEvent], data: ClassTag[Data])
  extends PersistentActor{

  case object RequestSnapshot
  case object StartQueryStream

  val snapshotFrequency: Int
  private var queryStreamStarted = false

  val persistenceIdtoQuery: String

  val queryJournalForPersistentId:
    (String, Long) => Source[EventEnvelope, Unit]

  var cachedData: Data

  def bookKeeping(): Unit ={
    cachedData.offset+=1
    if (cachedData.offset % snapshotFrequency == 0)
      self ! RequestSnapshot
  }

  val receiveRecover: Receive = {
    case SnapshotOffer(_, data(cache)) =>
      cachedData = cache

    case RecoveryCompleted =>
      self ! StartQueryStream
  }

  //read commands reading off details from cachedData.
  val receiveReadCommand: Receive

  val receiveQueryViewCommand: Receive = {

    case RequestSnapshot ⇒
      saveSnapshot(cachedData)

    case StartQueryStream ⇒

      if(!queryStreamStarted)
        {
          queryStreamStarted=true
          implicit val materializer = ActorMaterializer()
          val events= queryJournalForPersistentId(persistenceIdtoQuery,cachedData.offset)
          events.runWith(Sink.actorRef(self, None))
        }

    case EventEnvelope(_,_,_,domainEvent(evt)) ⇒
      updateCache(evt)

    //internal events such as FSM state change which is private
    case EventEnvelope(_,_,_,_) ⇒ bookKeeping()

    case evt ⇒ println(s"didn't expect: $evt")
  }

  val receiveCommand: Receive = receiveQueryViewCommand orElse receiveReadCommand

  def updateCache(evt: DomainEvent): Unit

}
