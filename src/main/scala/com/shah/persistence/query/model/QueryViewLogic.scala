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

trait QueryViewLogic {

  def viewId: String

  def queryId: String

  protected var offsetForNextFetch: Long = 1

  type SnapshotData

  def saveSnapshot(): Unit

  def applySnapshot(updatedData: SnapshotData): Unit

  implicit val snapshotData: ClassTag[SnapshotData]

}

//This needs to be mixed in to create and enable the pipelines to be assembled.
trait QueryViewLogicImpl extends Snapshotter
  with ActorLogging with QueryViewLogic with ReadJournalQuerySupport {

  implicit val ec: ExecutionContext
  val timeoutDuration = 3 seconds
  implicit val timeout = Timeout(timeoutDuration)

  import QueryViewLogicImpl._

  var snapshotRequested = false

  val snapshotFrequency: Int
  val sequenceSnapshotterRef: ActorRef = context.actorOf(QueryViewSequenceApi.props(viewId))

  import akka.persistence.SaveSnapshotSuccess

  def unhandledCommand: Receive = {
    case SaveSnapshotSuccess(_) ⇒ //nothing
    case event                  ⇒
      log.error(s"un-caught event: $event")
  }

  def recevieInternal: Receive = performSnapshot orElse readOffsetValue

  def performSnapshot: Receive = {
    case RequestSnapshot ⇒
      val offsetUpdated = sequenceSnapshotterRef ? QueryViewSequenceApi.UpdateSequenceNr(offsetForNextFetch)

      offsetUpdated map {
        _ ⇒
          saveSnapshot()
          val inboundAcknowldegment = sender() ? QueryViewSequenceApi.OffsetUpdateOutBoundAcknowledgement
          inboundAcknowldegment.map {
            _ ⇒ snapshotRequested = false
          } recover { case _ ⇒ self ! RequestSnapshot }
      } recover {
        case _ ⇒
          self ! RequestSnapshot
          log.debug("QueryViewSequenceSnapshotter not reachable. Will try again.")
      }
  }

  def readOffsetValue: Receive = {
    case ReadOffsetSequence ⇒
      import com.shah.persistence.query.model.QueryViewSequenceApi.{GetLastSnapshottedSequenceNr, QuerryOffset}
      val lastSequenceNr = (sequenceSnapshotterRef ? GetLastSnapshottedSequenceNr).mapTo[QuerryOffset]
      lastSequenceNr onComplete {
        case Success(QuerryOffset(sequenceNr)) ⇒
          offsetForNextFetch = sequenceNr
          scheduleJournalEvents()

        case Failure(reason) ⇒
          log.info(s"offset sequence snapshotter failed to catch up: $reason." +
            "Requesting again shortly.")
          context.system.scheduler.scheduleOnce(1 second, self, ReadOffsetSequence)
      }
  }

  def bookKeeping(): Unit = {
    offsetForNextFetch += 1
    if (offsetForNextFetch % snapshotFrequency == 0 && !snapshotRequested) {
      snapshotRequested = true
      self ! RequestSnapshot
    }
  }

  def queryViewCommandPipeline: PartialFunction[Any, Any] = {
    case EventEnvelope(_, _, _, event) ⇒
      bookKeeping()
      sender() ! PersistedEventProcessed
      event
    case readEvent                     ⇒
      readEvent //pass them on
  }

  val streamParallelism: Int

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
      self ! ReadOffsetSequence
  }

}

object QueryViewLogicImpl {

  private[QueryViewLogicImpl] case object PersistedEventProcessed

  private[QueryViewLogicImpl] case object RequestSnapshot

  private[QueryViewLogicImpl] case object ReadOffsetSequence

}