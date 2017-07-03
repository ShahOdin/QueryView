package com.shah.persistence.query.model

import akka.actor.ActorRef
import akka.persistence.{RecoveryCompleted, SnapshotOffer, Snapshotter}
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import akka.pattern.ask
import com.shah.persistence.query.model.QVSSnapshotter.API

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

  implicit val materializer: ActorMaterializer
}

case object StartQueryStream

trait QueryViewImplBase[D] extends QueryViewBase{
  var cachedData: D
  implicit val data: ClassTag[D]

  def queryJournalFrom(idToQuery: String, queryOffset: Long): Source[EventEnvelope, Unit]

  def bookKeeping(): Unit = {
    println(s"book keeping with: $offsetForNextFetch")

    for{
      API.QuerryOffset(sequenceNr) ← SequenceSnapshotterRef ? QVSApi.IncrementFromSequenceNr
    } {
      println(s"incremented $sequenceNr")
      offsetForNextFetch = sequenceNr
    }
//todo: add +1 after fixing the life cycyle
    if (offsetForNextFetch % snapshotFrequency == 0)
    {
      saveSnapshot(cachedData)
    }
  }

  abstract override def preStart() = {
    super.preStart()
    val SequenceSnapshotterRef = context.actorOf(QVSApi.props(viewId, snapshotFrequency))
  }

  def QueryViewCommandPipeline: PartialFunction[Any, Any] = {
    case StartQueryStream ⇒
      if(!queryStreamStarted)
      {
        queryStreamStarted=true
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
        API.QuerryOffset(sequenceNr) ← SequenceSnapshotterRef ? QVSApi.GetLastSnapshottedSequenceNr
      } {
        println(s"snapshot recovered: $sequenceNr")
        offsetForNextFetch = sequenceNr
      }
      println(s"snapshot recovered: $offsetForNextFetch")
      self ! StartQueryStream
  }
}

