package com.shah.persistence.query.model

case object StartQueryStream

trait QueryView[D] extends QueryViewBase[D] {

  override def receiveRecover: Receive = receiveQueryViewSnapshot

  abstract override def receiveCommand: Receive = {
    receiveQueryViewCommand andThen super.receiveCommand
  }

}
