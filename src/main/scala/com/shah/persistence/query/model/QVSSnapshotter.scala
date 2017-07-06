package com.shah.persistence.query.model

import akka.actor.Props
import akka.persistence.{PersistentActor, SnapshotOffer}

object QVSApi {

  //commands
  case class UpdateSequenceNr(from: Long)

  case object GetLastSnapshottedSequenceNr

  //responses
  case class QuerryOffset(from: Long)

  case object OffsetUpdated

  def props(viewId: String): Props = Props(new QVSSnapshotter(viewId))

}

object QVSSnapshotter {
  val API = QVSApi
  val IdSuffix = "-SequenceSnapshotter"
}

// QVS: QueryViewSequence
class QVSSnapshotter(viewId: String) extends PersistentActor {

  import QVSSnapshotter._

  private var offsetForNextFetch: Long = 1L

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, nextOffset: Long) ⇒
      offsetForNextFetch = nextOffset
  }

  override def receiveCommand: Receive = {

    case API.GetLastSnapshottedSequenceNr ⇒
      sender() ! API.QuerryOffset(offsetForNextFetch)

    case API.UpdateSequenceNr(from: Long) ⇒
      if (from > offsetForNextFetch) {
        offsetForNextFetch = from
        saveSnapshot(offsetForNextFetch)
        sender() ! API.OffsetUpdated
      }
  }

  override def persistenceId: String = viewId + IdSuffix
}

