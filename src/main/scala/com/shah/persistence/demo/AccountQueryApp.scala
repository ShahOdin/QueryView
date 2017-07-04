package com.shah.persistence.demo

import akka.actor.{ActorSystem, Props}
import com.shah.persistence.demo.Account.{CR, DR, Operation}
import com.shah.persistence.demo.AccountViewApi.ReadAccountBalance
import com.shah.persistence.query.model.{LeveldBInspector, PrintEvents}

import scala.concurrent.ExecutionContext.Implicits.global

//reads Account events from journal via queries.
object AccountQueryApp extends App {

  val system: ActorSystem = ActorSystem("AccountQueryApp")

  val account = system.actorOf(Props[Account])

  //val reader = system.actorOf(AccountView.props(5))
  val reader = system.actorOf(AccountFSMView.props(5))

  account ! Operation(400, CR)
  account ! Operation(200, DR)

  reader ! ReadAccountBalance
  Thread.sleep(3000)
  reader ! ReadAccountBalance

  Thread.sleep(1000)
  system.terminate()
}

//inspects and prints the events on the journal relating to a persistent Actor
object AccountInspectApp extends App{

  val system: ActorSystem = ActorSystem("AccountInspectApp")

  val inspector = system.actorOf(LeveldBInspector.props())
  inspector ! PrintEvents(Account.identifier)

  Thread.sleep(3000)
  system.terminate()
}