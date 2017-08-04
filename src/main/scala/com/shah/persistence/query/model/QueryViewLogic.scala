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
  private var snapshotProcessedAndWaitingForOffsetEvent = false
  private var lastSnapshotSequenceNr = 0L
  private var attemptsAtPendingSnapshotCall: Int = 0

  import akka.persistence.SnapshotMetadata

  def receiveQueryViewSnapshot: Receive = {
    case SnapshotOffer(SnapshotMetadata(_, sequenceNr, _), snapshotData(cache)) ⇒
      snapshotProcessedAndWaitingForOffsetEvent = true
      lastSnapshotSequenceNr = sequenceNr
      applySnapshot(cache)

    case OffsetEvent(from) ⇒
      updateOffset(from)
      snapshotProcessedAndWaitingForOffsetEvent = false

    case RecoveryCompleted ⇒
      if (snapshotProcessedAndWaitingForOffsetEvent) //ie if persistence failed on the offsetEvent.
        tryDeleteLastSnapshot()
      else
        scheduleJournalEvents()
  }

  val streamParallelism: Int

  def scheduleJournalEvents() = {
    val events = queryJournalFrom(queryId, offsetForNextFetch)
    implicit val materializer = ActorMaterializer()(context.system)
    events.mapAsync(parallelism = streamParallelism)(elem ⇒ self ? elem)
      .runWith(Sink.ignore)
  }

  def trySaveSnapshot(): Unit = {
    saveSnapshot()
    val duration = getSnapshotCallWaitDuration()
    context.system.scheduler.scheduleOnce(duration, self, CheckSnapshotSaved)
  }

  def tryDeleteLastSnapshot(): Unit = {
    deleteSnapshot(lastSnapshotSequenceNr)
    val duration = getSnapshotCallWaitDuration()
    context.system.scheduler.scheduleOnce(duration, self, CheckSnapshotDeleted)
  }

  import akka.persistence.{SaveSnapshotSuccess, SaveSnapshotFailure, DeleteSnapshotSuccess, DeleteSnapshotFailure}

  def takeSnapshot: Receive = {

    case StartSnapshotProcess ⇒
      snapshotInProgress = true
      trySaveSnapshot()

    case SaveSnapshotFailure ⇒
      log.error(s"saving snapshot failed. Offset = ${offsetForNextFetch}. retry in progress.")

    case CheckSnapshotSaved ⇒
      if (attemptsAtPendingSnapshotCall != 0)
        trySaveSnapshot()

    case SaveSnapshotSuccess(_) ⇒
      persist(OffsetEvent(offsetForNextFetch)) { case OffsetEvent(from) ⇒ updateOffset(from) }
      snapshotInProgress = false
      attemptsAtPendingSnapshotCall = 0
      unstashAll()

    case DeleteSnapshotFailure(_, _) ⇒
      log.error(s"deleting snapshot failed. Offset = ${offsetForNextFetch}. retry in progress.")

    case CheckSnapshotDeleted ⇒
      tryDeleteLastSnapshot()

    case DeleteSnapshotSuccess(_) ⇒
      log.debug("incomplete snapshot attempt deleted from the snapshot store. " +
        "restarting to revert to previous snapshot.")
      attemptsAtPendingSnapshotCall = 0
      context.stop(self)

    case _ if snapshotInProgress ⇒
      stash()
  }

  def getSnapshotCallWaitDuration(): FiniteDuration = {
    def getExponentialWaitDuration(attemptsMade: Int): FiniteDuration = {
      val attemptNrCap = 5
      val minimumWait = 1
      val cappedAttemptNr = if (attemptsMade > attemptNrCap) attemptNrCap else attemptsMade
      val randomVariation = scala.util.Random.nextDouble() - 0.5f
      val waitDuration = scala.math.pow(2, cappedAttemptNr) + randomVariation
      waitDuration seconds
    }

    attemptsAtPendingSnapshotCall += 1
    getExponentialWaitDuration(attemptsAtPendingSnapshotCall)
  }


  def updateOffset(from: Long) = {
    offsetForNextFetch = from
  }

  def queryViewCommandPipeline: PartialFunction[Any, Any] = {
    case EventEnvelope(_, _, _, event) ⇒
      bookKeeping()
      sender() ! PersistedEventProcessed
      event
    case readEvent                     ⇒
      readEvent //pass them on
  }

  def bookKeeping(): Unit = {
    offsetForNextFetch += 1
    if (offsetForNextFetch % snapshotFrequency == 0) {
      self ! StartSnapshotProcess
    }
  }

  def unhandledCommand: Receive = {
    case event ⇒
      log.error(s"ignored event: $event")
  }
}

object QueryViewLogicImpl {

  private[QueryViewLogicImpl] case object PersistedEventProcessed

  private[QueryViewLogicImpl] case object StartSnapshotProcess

  private[QueryViewLogicImpl] case object CheckSnapshotDeleted

  private[QueryViewLogicImpl] case object CheckSnapshotSaved

  private[QueryViewLogicImpl] case class OffsetEvent(from: Long)

}