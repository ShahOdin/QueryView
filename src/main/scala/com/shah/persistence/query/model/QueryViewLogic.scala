package com.shah.persistence.query.model

import akka.actor.{ActorLogging, Stash}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import akka.pattern.ask

import scala.concurrent.duration._

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
trait QueryViewLogicImpl extends PersistentActor
  with Stash with ActorLogging with QueryViewLogic with ReadJournalQuerySupport {

  implicit val ec: ExecutionContext
  val timeoutDuration = 3 seconds
  implicit val timeout = Timeout(timeoutDuration)

  import QueryViewLogicImpl._

  val snapshotFrequency: Int

  private var snapshotInProgress = false

  def unhandledCommand: Receive = {
    case event                  ⇒
      log.error(s"ignored event: $event")
  }

  def bookKeeping(): Unit = {
    offsetForNextFetch += 1
    if (offsetForNextFetch % snapshotFrequency == 0) {
      snapshotInProgress = true
      self ! StartSnapshotProcess
    }
  }

  def updateOffset(from: Long) ={
    log.info(s"updating the offset value from ${offsetForNextFetch} to ${from}")
    offsetForNextFetch = from
  }

  import akka.persistence.{SaveSnapshotSuccess,SaveSnapshotFailure}
  def takeSnapshot: Receive = {
    case StartSnapshotProcess ⇒
      log.info(s"snapshot started with offset: ${offsetForNextFetch}")
      saveSnapshot()

    case SaveSnapshotFailure ⇒
      log.info(s"snapshot failed with offset: ${offsetForNextFetch}, retrying...")
      saveSnapshot()

    case SaveSnapshotSuccess(_)  ⇒
      persist(OffsetEvent(offsetForNextFetch)){ case OffsetEvent(from) ⇒ updateOffset(from) }
      snapshotInProgress = false
      unstashAll()
      log.info("snapshot finished.")
    case command if snapshotInProgress ⇒
      log.info(s"message being stashed: ${command}")
      stash()
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

    case OffsetEvent(from) ⇒
      updateOffset(from)

    case RecoveryCompleted ⇒
      log.info(s"recovery completed with offset: ${offsetForNextFetch}")
      scheduleJournalEvents()
  }

}

object QueryViewLogicImpl {

  private[QueryViewLogicImpl] case object PersistedEventProcessed

  private[QueryViewLogicImpl] case object StartSnapshotProcess

  private[QueryViewLogicImpl] case class OffsetEvent(from: Long)
}