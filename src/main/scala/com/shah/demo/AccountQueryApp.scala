package com.shah.demo

import akka.actor.{ActorSystem, Props}
import com.shah.demo.Account.{CR, DR, Operation}
import com.shah.model.query.PrintEvents

//reads Account events from journal via queries.

object AccountQueryApp extends App {

  val system: ActorSystem = ActorSystem("persistent-query")

  val account = system.actorOf(Props[Account])

  //inspector prints the events related to persistentActor. for debugging purposes.
  //val inspector = system.actorOf(Props[AccountInspector])
  //inspector ! PrintEvents(Account.identifier)

  val reader = system.actorOf(AccountReader.props())

  account ! Operation(400, CR)
  account ! Operation(200, DR)

  reader ! ReadAccountBalance
  Thread.sleep(5000)
  reader ! ReadAccountBalance

  system.terminate()

}