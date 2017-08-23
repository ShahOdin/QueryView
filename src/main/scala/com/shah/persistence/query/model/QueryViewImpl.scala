package com.shah.persistence.query.model

import akka.persistence.PersistentActor

import scala.reflect.ClassTag
//This is the Persistent-Actor implementation of QueryView.
//One could potentially have a PersistentFSM implementation of these two entities.

//The view Persistent-Actors can mix this trait in to specify the building blocks of QueryView.
abstract class QueryViewBase[D](implicit val snapshotData: ClassTag[D])
  extends PersistentActor with QueryViewLogic {

  type SnapshotData = D

  def persistenceId: String = viewId

  import akka.actor.Actor.emptyBehavior
  def receiveRecover: Receive = emptyBehavior

  def receiveCommand: Receive = receiveReads orElse receiveJournalEvents

  def receiveJournalEvents: Receive

  def receiveReads: Receive
}

//The view Persistent-Actors extending QueryViewBase need to mix this in to get the QueryView machinery working.
trait QueryViewImpl extends PersistentActor with QueryViewLogicImpl {

  override def receiveRecover: Receive = receiveQueryViewSnapshot

  abstract override def receiveCommand: Receive = {
    internalSnapshotRelated orElse (queryViewCommandPipeline andThen (super.receiveCommand orElse unhandledCommand))
  }

}
