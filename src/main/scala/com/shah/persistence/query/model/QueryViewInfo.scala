package com.shah.persistence.query.model

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.{RecoveryCompleted, SnapshotOffer, Snapshotter}
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import akka.pattern.ask

import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait QueryViewInfo {

  def viewId: String

  def queryId: String

  protected var offsetForNextFetch: Long = 1

  type SnapshotData

  def saveSnapshot(): Unit

  def applySnapshot(updatedData: SnapshotData): Unit

  implicit val snapshotData: ClassTag[SnapshotData]

}

//This needs to be mixed in to create and enable the pipelines to be assembled.
trait QueryViewImplBase extends Snapshotter
  with ActorLogging with QueryViewInfo with ReadJournalQuerySupport {

  implicit val ec: ExecutionContext
  val timeoutDuration = 3 seconds
  implicit val timeout = Timeout(timeoutDuration)
  import QueryViewImplBase._

  val snapshotFrequency: Int
  val sequenceSnapshotterRef: ActorRef = context.actorOf(QueryViewSequenceApi.props(viewId))

  def unhandledCommand: Receive = {
    case event ⇒
      log.debug(s"un-caught event: $event")
  }

  def bookKeeping(): Unit = {
    offsetForNextFetch += 1
    implicit val ec = context.dispatcher
    if (offsetForNextFetch % snapshotFrequency == 0) {
      import scala.concurrent.Await
      val offsetUpdated = sequenceSnapshotterRef ? QueryViewSequenceApi.UpdateSequenceNr(offsetForNextFetch)
      offsetUpdated onComplete {
        case Success(value)      ⇒
          saveSnapshot()
        case Failure(reason) ⇒
          log.info(s"QueryView failed with reason: $reason")
          context.stop(self)
      }
      Await.result(offsetUpdated,timeoutDuration)
    }
  }

  def QueryViewCommandPipeline: PartialFunction[Any, Any] = {
    case EventEnvelope(_, _, _, event) ⇒
      bookKeeping()
      sender() ! PersistedEventProcessed
      event
    case readEvent                     ⇒
      readEvent //pass them on to the class mixing the trait.
  }

  val streamParallelism: Int = 5

  def scheduleJournalEvents() = {
    val events = queryJournalFrom(queryId, offsetForNextFetch)
    implicit val materializer = ActorMaterializer()(context.system)
    events.mapAsync(parallelism = streamParallelism)(elem ⇒ self ? elem)
      .runWith(Sink.ignore)
  }

  def receiveQueryViewSnapshot: Receive = {
    case SnapshotOffer(_, snapshotData(cache)) ⇒
      applySnapshot(cache)

    case RecoveryCompleted ⇒
      import com.shah.persistence.query.model.QueryViewSequenceApi.{GetLastSnapshottedSequenceNr, QuerryOffset}

      import scala.concurrent.Await
      implicit val ec = context.dispatcher
      val lastSequenceNr = (sequenceSnapshotterRef ? GetLastSnapshottedSequenceNr).mapTo[QuerryOffset]
      lastSequenceNr onComplete {
        case Success(QuerryOffset(sequenceNr)) ⇒
          offsetForNextFetch = sequenceNr
          scheduleJournalEvents()

        case Failure(reason) ⇒
          log.info(s"offset sequence snapshotter failed to catch up: $reason." +
            " Resorting to manual updating of cache based on all events.")
          scheduleJournalEvents()
      }
      Await.result(lastSequenceNr, timeoutDuration)

  }

}

object QueryViewImplBase {
  private[QueryViewImplBase] case object PersistedEventProcessed
}