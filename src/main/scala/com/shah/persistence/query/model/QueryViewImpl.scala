package com.shah.persistence.query.model

import akka.persistence.PersistentActor

//The view Persistent Actors can mix-in this trait to specify the main logic of the view actor.
trait QueryViewBase extends PersistentActor with QueryViewInfo {
  def receiveRecover: Receive = Map.empty
}

//The view actor implementations need to mix-in this to get the pipelines working together.
trait QueryViewImpl[D] extends QueryViewImplBase[D] {

  override def receiveRecover: Receive = receiveQueryViewSnapshot

  abstract override def receiveCommand: Receive = {
    QueryViewCommandPipeline andThen (super.receiveCommand orElse unhandledCommand)
  }

}
