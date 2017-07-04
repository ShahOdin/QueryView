package com.shah.persistence.query.model

import akka.actor.ActorRef
import akka.persistence.{RecoveryCompleted, SnapshotOffer, Snapshotter}
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import akka.pattern.ask
import com.shah.persistence.query.model.QVSSnapshotter.API

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

trait QueryViewBase extends Snapshotter{

  def viewId: String
  def queryId: String

  def persistenceId: String = viewId

  protected var queryStreamStarted = false

  protected var offsetForNextFetch: Long = 1

  implicit val materializer: ActorMaterializer

  def receiveRecover: Receive = Map.empty
}

case object StartQueryStream

trait QueryViewImplBase[D] extends QueryViewBase{
  var cachedData: D
  implicit val data: ClassTag[D]

  implicit val ec: ExecutionContext
  implicit val timeout = Timeout(3 seconds)

  val snapshotFrequency:Int
  var sequenceSnapshotterRef: ActorRef = context.actorOf(QVSApi.props(viewId))

  def queryJournalFrom(idToQuery: String, queryOffset: Long): Source[EventEnvelope, Unit]

  def bookKeeping(): Unit = {
    offsetForNextFetch += 1

    if (offsetForNextFetch % (snapshotFrequency+1) == 0)
    {
      val offsetUpdated = sequenceSnapshotterRef ? API.UpdateSequenceNr(offsetForNextFetch)

      offsetUpdated onComplete{
        case Success(v) ⇒
          saveSnapshot(cachedData)
        case Failure(t) ⇒
          context.stop(self)
      }
    }
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
        API.QuerryOffset(sequenceNr) ← sequenceSnapshotterRef ? QVSApi.GetLastSnapshottedSequenceNr
      } {
        offsetForNextFetch = sequenceNr
      }
      self ! StartQueryStream
  }
}

