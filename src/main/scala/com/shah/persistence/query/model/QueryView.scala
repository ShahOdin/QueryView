package com.shah.persistence.query.model

import akka.persistence.query.EventEnvelope
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.reflect.ClassTag

case object RequestSnapshot
case object StartQueryStream

abstract class QueryView[D<: QueryViewData](implicit data: ClassTag[D])
  extends PersistentActor{

  val snapshotFrequency: Int
  private var queryStreamStarted = false

  val persistenceIdtoQuery: String

  def queryJournalFrom(idToQuery: String, queryOffset: Long): Source[EventEnvelope, Unit]

  var cachedData: D

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

  //read commands for reading information derived from cachedData.
  val receiveReadCommand: Receive

  val receiveQueryViewCommand: Receive = {
    case StartQueryStream ⇒
      if(!queryStreamStarted)
        {
          queryStreamStarted=true
          implicit val materializer = ActorMaterializer()
          val events= queryJournalFrom(persistenceIdtoQuery,cachedData.offsetForNextFetch)
          events.map(self ! _).runWith(Sink.ignore)
        }

    case EventEnvelope(_,_,_,event) ⇒
      updateCache.lift(event)
      bookKeeping()
  }

  def receiveCommand: Receive = receiveQueryViewCommand orElse receiveReadCommand

  def updateCache: Receive
}
