package com.shah.persistence.query.model

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.pattern.ask

object QueryViewSequenceSnapshotter {
  val IdSuffix = "-SequenceSnapshotter"
}

package object QueryViewSequenceApi {

  //commands
  case class UpdateSequenceNr(from: Long)

  case object GetLastSnapshottedSequenceNr

  //responses
  case class QuerryOffset(from: Long)

  case object OffsetUpdated

  case object OffsetUpdateOutBoundAcknowledgement

  case object OffsetUpdateInBoundAcknowledgement

  def props(viewId: String): Props = Props(new QueryViewSequenceSnapshotter(viewId))
}

class QueryViewSequenceSnapshotter(viewId: String) extends PersistentActor
  with ActorLogging {

  import QueryViewSequenceSnapshotter._

  private var offsetForNextFetch: Long = 1L
  private var lastSnapshottedValue: Long = 1L

  import akka.util.Timeout

  import scala.concurrent.duration._
  implicit val timeout = Timeout(3 seconds)

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, nextOffset: Long) ⇒
      offsetForNextFetch = nextOffset
  }

  import com.shah.persistence.query.model.QueryViewSequenceApi._

  def updateOffset(updatedOffset: Long) = {
    offsetForNextFetch = updatedOffset
    saveSnapshot(offsetForNextFetch)
  }

  override def receiveCommand: Receive = {
    case GetLastSnapshottedSequenceNr ⇒
      sender() ! QuerryOffset(offsetForNextFetch)

    case UpdateSequenceNr(from: Long) ⇒
      if (from > offsetForNextFetch) {

        import scala.concurrent.ExecutionContext
        implicit val ec: ExecutionContext = context.dispatcher

        updateOffset(from)

        val outboundAcknowledgement = sender() ? OffsetUpdated
        outboundAcknowledgement.map {
          _ ⇒
            log.info("QueryViewSequenceSnapshotter update was acknowledged.")
            sender() ! OffsetUpdateInBoundAcknowledgement
        } recover {
          case _ ⇒ //reverting the update
            updateOffset(lastSnapshottedValue)
            log.error(s"QueryViewSequenceSnapshotter update was not acknowledged. " +
              s"Reverting the snapshot update.")
        }
      }
      else {
        log.error("QueryViewSequenceSnapshotter update rejected.")
      }
  }

  override def persistenceId: String = viewId + IdSuffix
}
