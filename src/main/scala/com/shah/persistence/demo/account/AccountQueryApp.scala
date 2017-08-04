package com.shah.persistence.demo.account

import akka.actor.ActorSystem

import scala.concurrent.ExecutionContext.Implicits.global

//reads Account events from journal via queries.
object AccountQueryApp extends App {

  import com.shah.persistence.demo.AccountViewApi
  import com.shah.persistence.demo.AccountApi

  val system: ActorSystem = ActorSystem("AccountQueryApp")

  val account = AccountApi.startActor(system)

  val reader = system.actorOf(AccountViewApi.props(3))

  account ! AccountApi.Operation(4000, AccountApi.CR)
  account ! AccountApi.Operation(1000, AccountApi.DR)
  account ! AccountApi.Operation(3000, AccountApi.CR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(500, AccountApi.DR)
  account ! AccountApi.Operation(6500, AccountApi.CR)
  account ! AccountApi.Operation(6000, AccountApi.CR)
  account ! AccountApi.Operation(5000, AccountApi.DR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(4000, AccountApi.CR)
  account ! AccountApi.Operation(1000, AccountApi.DR)
  account ! AccountApi.Operation(3000, AccountApi.CR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(500, AccountApi.DR)
  account ! AccountApi.Operation(6500, AccountApi.CR)
  account ! AccountApi.Operation(6000, AccountApi.CR)
  account ! AccountApi.Operation(5000, AccountApi.DR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(4000, AccountApi.CR)
  account ! AccountApi.Operation(1000, AccountApi.DR)
  account ! AccountApi.Operation(3000, AccountApi.CR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(500, AccountApi.DR)
  account ! AccountApi.Operation(6500, AccountApi.CR)
  account ! AccountApi.Operation(6000, AccountApi.CR)
  account ! AccountApi.Operation(5000, AccountApi.DR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(4000, AccountApi.CR)
  account ! AccountApi.Operation(1000, AccountApi.DR)
  account ! AccountApi.Operation(3000, AccountApi.CR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(500, AccountApi.DR)
  account ! AccountApi.Operation(6500, AccountApi.CR)
  account ! AccountApi.Operation(6000, AccountApi.CR)
  account ! AccountApi.Operation(5000, AccountApi.DR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(4000, AccountApi.CR)
  account ! AccountApi.Operation(1000, AccountApi.DR)
  account ! AccountApi.Operation(3000, AccountApi.CR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(500, AccountApi.DR)
  account ! AccountApi.Operation(6500, AccountApi.CR)
  account ! AccountApi.Operation(6000, AccountApi.CR)
  account ! AccountApi.Operation(5000, AccountApi.DR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(4000, AccountApi.CR)
  account ! AccountApi.Operation(1000, AccountApi.DR)
  account ! AccountApi.Operation(3000, AccountApi.CR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(500, AccountApi.DR)
  account ! AccountApi.Operation(6500, AccountApi.CR)
  account ! AccountApi.Operation(6000, AccountApi.CR)
  account ! AccountApi.Operation(5000, AccountApi.DR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(4000, AccountApi.CR)
  account ! AccountApi.Operation(1000, AccountApi.DR)
  account ! AccountApi.Operation(3000, AccountApi.CR)
  account ! AccountApi.Operation(1500, AccountApi.DR)
  account ! AccountApi.Operation(500, AccountApi.DR)
  account ! AccountApi.Operation(6500, AccountApi.CR)
  account ! AccountApi.Operation(6000, AccountApi.CR)
  account ! AccountApi.Operation(5000, AccountApi.DR)
  account ! AccountApi.Operation(1500, AccountApi.DR)

  reader ! AccountViewApi.PrintAccountBalance
  Thread.sleep(1000)
  reader ! AccountViewApi.PrintAccountBalance

  Thread.sleep(4000)
  system.terminate()
}

//inspects and prints the events on the journal relating to a persistent Actor
object AccountInspectApp extends App {

  val system: ActorSystem = ActorSystem("AccountInspectApp")

  import com.shah.persistence.query.model.{LeveldBInspector, PrintEvents}

  val inspector = system.actorOf(LeveldBInspector.props())
  inspector ! PrintEvents(Account.identifier)

  Thread.sleep(3000)
  system.terminate()
}