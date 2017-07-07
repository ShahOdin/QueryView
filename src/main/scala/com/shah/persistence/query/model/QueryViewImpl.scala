package com.shah.persistence.query.model

import akka.persistence.PersistentActor

import scala.reflect.ClassTag
//This is the PersistentView implementation of the read side.
//One could potentially have a PersistentFSM implementation of
// these two classes. the need for this use case is debatable.

//The view Persistent Actors inherits from this abstract class
// to provide the resources needed for the Query-view mechanism.
abstract class QueryViewBase[D](implicit val snapshotData: ClassTag[D])
  extends PersistentActor with QueryViewInfo {

  type SnapshotData = D

  def persistenceId: String = viewId

  def receiveRecover: Receive = Map.empty

  def receiveCommand: Receive = receiveReads orElse receiveJournalEvents

  def receiveJournalEvents: Receive

  def receiveReads: Receive
}

//The view actor implementations need to mix-in this to get the pipelines working together.
trait QueryViewImpl extends PersistentActor with QueryViewImplBase {

  override def receiveRecover: Receive = receiveQueryViewSnapshot

  abstract override def receiveCommand: Receive = {
    QueryViewCommandPipeline andThen (super.receiveCommand orElse unhandledCommand)
  }

}
