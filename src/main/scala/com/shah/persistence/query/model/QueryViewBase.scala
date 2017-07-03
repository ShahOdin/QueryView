package com.shah.persistence.query.model

import akka.actor.ActorRef
import akka.persistence.{RecoveryCompleted, SnapshotOffer, Snapshotter}
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait QueryViewBase extends Snapshotter{

  implicit val ec: ExecutionContext
  implicit val timeout = Timeout(3 seconds)

  def viewId: String
  def queryId: String

  def persistenceId: String = viewId

  protected var queryStreamStarted = false

  var SequenceSnapshotterRef: ActorRef = _
  val snapshotFrequency:Int
  protected var offsetForNextFetch: Long = 1

}

case object StartQueryStream

trait QueryViewImplBase[D] extends QueryViewBase{
  var cachedData: D
  implicit val data: ClassTag[D]

  def queryJournalFrom(idToQuery: String, queryOffset: Long): Source[EventEnvelope, Unit]

  def bookKeeping(): Unit = {
    for{
      sequenceNr ← (SequenceSnapshotterRef ? QVSApi.IncrementFromSequenceNr).mapTo[Long]
    } {
      println(s"incremented $sequenceNr")
      offsetForNextFetch = sequenceNr
    }

    if (offsetForNextFetch % snapshotFrequency == 0)
    {
      saveSnapshot(cachedData)
    }
  }

  abstract override def preStart() = {
    super.preStart()
    val SequenceSnapshotterRef = context.actorOf(QVSApi.props(viewId,snapshotFrequency))
  }

  def QueryViewCommandPipeline: PartialFunction[Any, Any] = {
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
      for{
        sequenceNr ← (SequenceSnapshotterRef ? QVSApi.GetLastSnapshottedSequenceNr).mapTo[Long]
      } {
        println(s"snapshot recovered: $sequenceNr")
        offsetForNextFetch = sequenceNr
      }
      println(s"snapshot recovered: $offsetForNextFetch")
      self ! StartQueryStream
  }
}

