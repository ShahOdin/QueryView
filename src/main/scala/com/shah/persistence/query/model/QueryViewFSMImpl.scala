package com.shah.persistence.query.model

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

trait QueryViewFSMImpl[S <: FSMState, D, E]
  extends PersistentFSM[S,D,E] with QueryViewImplBase[D]{

  override def applyEvent(domainEvent: E, currentData: D): D = ???
}
