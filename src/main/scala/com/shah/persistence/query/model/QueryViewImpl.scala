package com.shah.persistence.query.model

case object StartQueryStream

trait QueryViewImpl[D] extends QueryViewImplBase[D] {

  override def receiveRecover: Receive = receiveQueryViewSnapshot

  abstract override def receiveCommand: Receive = {
    receiveQueryViewCommand andThen super.receiveCommand
  }

}
