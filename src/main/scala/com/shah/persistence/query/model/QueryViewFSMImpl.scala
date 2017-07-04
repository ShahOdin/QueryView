package com.shah.persistence.query.model

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import com.shah.persistence.demo.Account

import scala.reflect.ClassTag



abstract class QueryViewFSMImpl[S <: FSMState, D, E]
(implicit var domainEventClassTag: ClassTag[E], override val data: ClassTag[D])
  extends PersistentFSM[S,D,E] with QueryViewImplBase[D]{

  def handleReads: Receive

  def unhandledRecovery: Receive = {
    case event ⇒
      println(s"un-caught recovery event: $event")
  }

  override def receiveRecover: Receive = {case _ ⇒}
  //receiveQueryViewSnapshot orElse unhandledRecovery

  override def receiveCommand: Receive = {case _ ⇒}
  //  QueryViewCommandPipeline andThen (handleReads orElse super.receiveCommand orElse unhandledCommand)

  override def applyEvent(domainEvent: E, currentData: D): D = {
    println("this should not execute.")
    currentData
  }

}
