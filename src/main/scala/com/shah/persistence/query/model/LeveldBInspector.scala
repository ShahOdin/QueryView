package com.shah.persistence.query.model

import akka.actor.{Actor, Props}

object LeveldBInspector {
  def props() = Props(new Actor() with QueryInspector with LeveldBQuerySupport)
}
