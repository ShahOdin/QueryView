package com.shah.model.query

import akka.persistence.query.EventEnvelope
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotSuccess, SnapshotOffer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.reflect.ClassTag

case object RequestSnapshot
case object StartQueryStream

abstract class QueryView[DomainEvent, SNData, Data<: SnapshottableQuerriedData[SNData]]
(implicit domainEvent: ClassTag[DomainEvent], data: ClassTag[Data])
  extends PersistentActor{

  val snapshotFrequency: Int
  private var queryStreamStarted = false

  val persistenceIdtoQuery: String

  def queryJournalFrom(idToQuery: String, queryOffset: Long)
  :Source[EventEnvelope, Unit]

  var cachedData: Data

  def bookKeeping(): Unit = {
    cachedData.offsetForNextFetch += 1
    if (cachedData.offsetForNextFetch % snapshotFrequency == 0)
      {
        saveSnapshot(cachedData)
      }
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

    case StartQueryStream ⇒

      if(!queryStreamStarted)
        {
          queryStreamStarted=true
          implicit val materializer = ActorMaterializer()
          val events= queryJournalFrom(persistenceIdtoQuery,cachedData.offsetForNextFetch)
          events.runWith(Sink.actorRef(self, None))
        }

    case EventEnvelope(_,_,_,domainEvent(evt)) ⇒
      updateCache(evt)

    //internal events such as FSM state change which is private
    case evt:EventEnvelope ⇒ bookKeeping()

    case SaveSnapshotSuccess(_) ⇒

    case evt ⇒
      println(s"unexpected event $evt")
  }

  val receiveCommand: Receive = receiveQueryViewCommand orElse receiveReadCommand

  def updateCache(evt: DomainEvent): Unit

}
