package com.shah.persistence.query.model

import akka.actor.Props
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SnapshotOffer}

object QVSApi {

  //commands
  case object IncrementFromSequenceNr
  case object GetLastSnapshottedSequenceNr

  //"event" ?
  case class QuerryOffset(from:Long)

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
      println(s"Snapshotter recovered snapshot with offset: $nextOffset")
      offsetForNextFetch = nextOffset
    case SaveSnapshotFailure(_, reason) =>
      println(s"save snapshot failed and failure is $reason")
  }

  def incrementOffset() ={
    offsetForNextFetch += 1
    println(s"incremented: $offsetForNextFetch")
  }

  def maybeSaveSnapshot()={
    if (incrementsSinceLastSnapshot > snapshotFrequency+1) {
      saveSnapshot(offsetForNextFetch)
      incrementsSinceLastSnapshot = 0
    } else {
      incrementsSinceLastSnapshot += 1
    }
  }

  override def receiveCommand: Receive = {
    case API.IncrementFromSequenceNr ⇒
      incrementOffset()
      maybeSaveSnapshot()
      sender() ! API.QuerryOffset(offsetForNextFetch)

    case API.GetLastSnapshottedSequenceNr ⇒
      sender() ! API.QuerryOffset(offsetForNextFetch)

  }

  override def persistenceId: String = viewId + IdSuffix
}

