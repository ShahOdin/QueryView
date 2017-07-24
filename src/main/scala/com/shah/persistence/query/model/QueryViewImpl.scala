package com.shah.persistence.query.model

import akka.persistence.PersistentActor

import scala.reflect.ClassTag
//This is the Persistent-Actor implementation of QueryView.
//One could potentially have a PersistentFSM implementation of these two entities.

//The view Persistent-Actors can mix this trait in to specify the building blocks of QueryView.
abstract class QueryViewBase[D](implicit val snapshotData: ClassTag[D])
  extends PersistentActor with QueryViewInfo {

  type SnapshotData = D

  def persistenceId: String = viewId

  def receiveRecover: Receive = Map.empty

  def receiveCommand: Receive = receiveReads orElse receiveJournalEvents

  def receiveJournalEvents: Receive

  def receiveReads: Receive
}

//The view actors extending QueryViewBase need to mix this in to get the QueryView machinery working.
trait QueryViewImpl extends PersistentActor with QueryViewImplBase {

  override def receiveRecover: Receive = receiveQueryViewSnapshot

  abstract override def receiveCommand: Receive = {
    QueryViewCommandPipeline andThen (super.receiveCommand orElse unhandledCommand)
  }

}
