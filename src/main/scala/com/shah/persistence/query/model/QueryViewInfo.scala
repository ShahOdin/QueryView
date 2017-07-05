package com.shah.persistence.query.model

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.shah.persistence.query.model.QVSSnapshotter.API

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import akka.pattern.ask

import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait QueryViewInfo {

  def viewId: String
  def queryId: String

  def persistenceId: String = viewId

  protected var queryStreamStarted = false

  protected var offsetForNextFetch: Long = 1
}

//This needs to be mixed in to create and enable the pipelines to be assembled.
trait QueryViewImplBase[D] extends PersistentActor with ActorLogging with QueryViewInfo{
  implicit val materializer: ActorMaterializer

  implicit val ec: ExecutionContext
  implicit val timeout = Timeout(3 seconds)

  val snapshotFrequency:Int
  val sequenceSnapshotterRef: ActorRef = context.actorOf(QVSApi.props(viewId))
  def queryJournalFrom(idToQuery: String, queryOffset: Long): Source[EventEnvelope, Unit]

  var cachedData: D
  implicit val data: ClassTag[D]

  def unhandledCommand: Receive = {
    case event ⇒
      log.debug(s"un-caught event: $event")
  }

  def bookKeeping(): Unit = {
    offsetForNextFetch += 1

    if (offsetForNextFetch % (snapshotFrequency+1) == 0)
    {
      val offsetUpdated = sequenceSnapshotterRef ? API.UpdateSequenceNr(offsetForNextFetch)

      offsetUpdated onComplete{
        case Success(_) ⇒
          saveSnapshot(cachedData)
        case Failure(reason) ⇒
          log.info(s"QueryView failed with reason: $reason")
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

case object StartQueryStream

