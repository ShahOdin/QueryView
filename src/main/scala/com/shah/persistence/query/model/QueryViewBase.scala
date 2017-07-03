package com.shah.persistence.query.model

import akka.actor.ActorRef
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.shah.persistence.query.model.{QVSApi ⇒ QVSSAPI}
import akka.util.Timeout
import akka.pattern.ask
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait QueryViewBase[D] extends PersistentActor{

  implicit val ec: ExecutionContext
  implicit val timeout = Timeout(5 seconds)

  def viewId: String
  def queryId: String

  override def persistenceId: String = viewId

  var cachedData: D
  implicit val data: ClassTag[D]

  protected var queryStreamStarted = false

  def queryJournalFrom(idToQuery: String, queryOffset: Long): Source[EventEnvelope, Unit]

  var SequenceSnapshotterRef: ActorRef = _
  val snapshotFrequency:Int
  protected var offsetForNextFetch: Long = 1

  override def preStart() = {
    val SequenceSnapshotterRef = context.actorOf(QVSSAPI.props(viewId,snapshotFrequency))
    for{
      sequenceNr ← (SequenceSnapshotterRef ? QVSSAPI.GetLastSnapshottedSequenceNr).mapTo[Long]
    } {offsetForNextFetch = sequenceNr }
    }

  def bookKeeping(): Unit = {
    SequenceSnapshotterRef ? QVSSAPI.IncrementFromSequenceNr
    if (offsetForNextFetch % snapshotFrequency == 0)
    {
      saveSnapshot(cachedData)
    }
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

}
