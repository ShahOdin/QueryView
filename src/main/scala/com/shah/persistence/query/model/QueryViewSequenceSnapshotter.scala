package com.shah.persistence.query.model

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, SnapshotOffer}

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

  def props(viewId: String): Props = Props(new QueryViewSequenceSnapshotter(viewId))
}

class QueryViewSequenceSnapshotter(viewId: String) extends PersistentActor
  with ActorLogging{

  import QueryViewSequenceSnapshotter._

  private var offsetForNextFetch: Long = 1L

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, nextOffset: Long) ⇒
      offsetForNextFetch = nextOffset
  }

  import com.shah.persistence.query.model.QueryViewSequenceApi._

  override def receiveCommand: Receive = {
    case GetLastSnapshottedSequenceNr ⇒
      sender() ! QuerryOffset(offsetForNextFetch)

    case UpdateSequenceNr(from: Long) ⇒
      if (from > offsetForNextFetch) {
        offsetForNextFetch = from
        saveSnapshot(offsetForNextFetch)
        sender() ! OffsetUpdated
      }
      else{
        log.error("QueryViewSequenceSnapshotter update rejected.")
      }
  }

  override def persistenceId: String = viewId + IdSuffix
}
