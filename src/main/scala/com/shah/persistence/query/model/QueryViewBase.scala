package com.shah.persistence.query.model

import akka.actor.ActorRef
import akka.persistence.{PersistentActor}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait QueryViewBase extends PersistentActor{

  implicit val ec: ExecutionContext
  implicit val timeout = Timeout(5 seconds)

  def viewId: String
  def queryId: String

  override def persistenceId: String = viewId

  protected var queryStreamStarted = false

  var SequenceSnapshotterRef: ActorRef = _
  val snapshotFrequency:Int
  protected var offsetForNextFetch: Long = 1

}

