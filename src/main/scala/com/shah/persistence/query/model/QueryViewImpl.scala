package com.shah.persistence.query.model

import akka.persistence.{RecoveryCompleted, SnapshotOffer}
import com.shah.persistence.query.model.{QVSApi ⇒ QVSSAPI}
import akka.pattern.ask
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.reflect.ClassTag

case object StartQueryStream

trait QueryViewImpl[D] extends QueryViewBase{

  var cachedData: D
  implicit val data: ClassTag[D]

  def queryJournalFrom(idToQuery: String, queryOffset: Long): Source[EventEnvelope, Unit]

  def bookKeeping(): Unit = {
    SequenceSnapshotterRef ? QVSSAPI.IncrementFromSequenceNr
    if (offsetForNextFetch % snapshotFrequency == 0)
    {
      saveSnapshot(cachedData)
    }
  }

  abstract override def preStart() = {
    super.preStart()
    val SequenceSnapshotterRef = context.actorOf(QVSSAPI.props(viewId,snapshotFrequency))
    for{
      sequenceNr ← (SequenceSnapshotterRef ? QVSSAPI.GetLastSnapshottedSequenceNr).mapTo[Long]
    } {offsetForNextFetch = sequenceNr }
  }

  def receiveQueryViewCommand: PartialFunction[Any, Any] = {
    case StartQueryStream ⇒
      if(!queryStreamStarted)
      {
        queryStreamStarted=true
        implicit val materializer = ActorMaterializer() //where should this go?
      val events= queryJournalFrom(queryId, offsetForNextFetch)
        events.map(self ! _).runWith(Sink.ignore)
      }
    case EventEnvelope(_,_,_,event) ⇒
      bookKeeping()
      event
    case readEvent ⇒
      readEvent //pass them on to the class mixing the trait.
  }

  def receiveQueryViewSnapshot : Receive = {
    case SnapshotOffer(_, data(cache)) =>
      cachedData = cache

    case RecoveryCompleted =>
      self ! StartQueryStream
  }

  override def receiveRecover: Receive = receiveQueryViewSnapshot

  abstract override def receiveCommand: Receive = {
    receiveQueryViewCommand andThen super.receiveCommand
  }
}
