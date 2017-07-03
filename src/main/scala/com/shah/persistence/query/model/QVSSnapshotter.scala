package com.shah.persistence.query.model

import akka.actor.Props
import akka.persistence.{PersistentActor, SnapshotOffer}

object QVSApi {

  case object IncrementFromSequenceNr
  case object GetLastSnapshottedSequenceNr

  def props(
             viewId:String,
             snapshotFrequency:Int
           ): Props =
    Props(new QVSSnapshotter(viewId, snapshotFrequency))

}

object QVSSnapshotter {
  val API = QVSApi
  val IdSuffix = "-SequenceSnapshotter"
}

//this class only snapshots without persisting the events.

// QVS: QueryViewSequence
class QVSSnapshotter(viewId:String,
                     snapshotFrequency:Int
                                  ) extends PersistentActor{
  import QVSSnapshotter._

  private var offsetForNextFetch: Long= 1L
  private var incrementsSinceLastSnapshot: Int= 0

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, nextOffset:Long) ⇒
      offsetForNextFetch = nextOffset
  }

  def incrementOffset ={
    offsetForNextFetch += 1
    println(s"incremented: $offsetForNextFetch")
  }

  override def receiveCommand: Receive = {
    case API.IncrementFromSequenceNr ⇒
      incrementOffset
      if (incrementsSinceLastSnapshot > snapshotFrequency) {
        saveSnapshot(offsetForNextFetch)
        incrementsSinceLastSnapshot = 0
      } else {
        incrementsSinceLastSnapshot += 1
      }
      sender() ! offsetForNextFetch

    case API.GetLastSnapshottedSequenceNr ⇒
      sender() ! offsetForNextFetch
  }

  override def persistenceId: String = viewId + IdSuffix
}

