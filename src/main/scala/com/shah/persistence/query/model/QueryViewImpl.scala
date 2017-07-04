package com.shah.persistence.query.model

import akka.persistence.PersistentActor

trait QueryViewImpl[D] extends PersistentActor with QueryViewImplBase[D]{

  override def receiveRecover: Receive = receiveQueryViewSnapshot

  def unhandledCommand: Receive = {
    case event ⇒
      println(s"un-caught event: $event")
  }

  abstract override def receiveCommand: Receive = {
    QueryViewCommandPipeline andThen (super.receiveCommand orElse unhandledCommand)
  }

}
