package com.shah.persistence.query.model

import akka.persistence.PersistentActor
//This is the PersistentView implementation of the read side.
//One could potentially have a PersistentFSM implementation of
// these two classes. the need for this use case is debatable.

//The view Persistent Actors can mix-in this trait to specify the main logic of the view actor.
trait QueryViewBase extends PersistentActor with QueryViewInfo {

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
