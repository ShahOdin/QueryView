package com.shah.persistence.demo.account

import akka.actor.{ActorSystem, Props}
import com.shah.persistence.query.model.{LeveldBInspector, PrintEvents}

import scala.concurrent.ExecutionContext.Implicits.global

//reads Account events from journal via queries.
object AccountQueryApp extends App {

  import com.shah.persistence.demo.account.Account._
  import com.shah.persistence.demo.AccountViewApi._

  val system: ActorSystem = ActorSystem("AccountQueryApp")

  val account = system.actorOf(Props[Account])

  val reader = system.actorOf(AccountView.props(5))

  account ! Operation(1000, CR)
  account ! Operation(500, DR)

  reader ! PrintAccountBalance
  Thread.sleep(3000)
  reader ! PrintAccountBalance

  Thread.sleep(2000)
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