package com.shah.demo

import akka.actor.{Actor, Props}
import com.shah.model.query.{LeveldBQuerySupport, QueryInspector}

class AccountInspector()
  extends Actor with QueryInspector with LeveldBQuerySupport

object AccountInspector {
  def props() = Props(new AccountInspector())
}
