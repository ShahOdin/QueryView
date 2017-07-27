package com.shah.persistence.demo.account

import akka.actor.{ActorSystem, Props}
import com.shah.persistence.query.model.{LeveldBInspector, PrintEvents}

import scala.concurrent.ExecutionContext.Implicits.global

//reads Account events from journal via queries.
object AccountQueryApp extends App {

  import com.shah.persistence.demo.AccountViewApi
  import com.shah.persistence.demo.AccountApi

  val system: ActorSystem = ActorSystem("AccountQueryApp")

  val account = system.actorOf(Props[Account])

  val reader = system.actorOf(AccountViewApi.props(3))

  account ! AccountApi.Operation(1000, AccountApi.CR)
  account ! AccountApi.Operation(500, AccountApi.DR)
  account ! AccountApi.Operation(1000, AccountApi.CR)
  account ! AccountApi.Operation(500, AccountApi.DR)

  reader ! AccountViewApi.PrintAccountBalance
  Thread.sleep(1000)
  reader ! AccountViewApi.PrintAccountBalance

  Thread.sleep(1000)
  system.terminate()
}

//inspects and prints the events on the journal relating to a persistent Actor
object AccountInspectApp extends App {

  val system: ActorSystem = ActorSystem("AccountInspectApp")

  val inspector = system.actorOf(LeveldBInspector.props())
  inspector ! PrintEvents(Account.identifier)

  Thread.sleep(3000)
  system.terminate()
}