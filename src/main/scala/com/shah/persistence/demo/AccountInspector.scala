package com.shah.persistence.demo

import akka.actor.{Actor, Props}
import com.shah.persistence.query.model.{LeveldBQuerySupport, QueryInspector}

class AccountInspector()
  extends Actor with QueryInspector with LeveldBQuerySupport

object AccountInspector {
  def props() = Props(new AccountInspector())
}
