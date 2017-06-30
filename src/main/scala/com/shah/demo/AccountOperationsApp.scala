package com.shah.demo

import akka.actor.{ActorSystem, Props}

//populates the journal with some events for the Account Persistent Actor.
object AccountOperationsApp extends App {
 import Account._

 val system = ActorSystem("AccountOperationsApp")

 val account = system.actorOf(Props[Account])

 account ! Operation(1000, CR)
 account ! Operation(500, CR)
 account ! Operation(50, DR)
 account ! Operation(100, DR)

 Thread.sleep(1000)

 system.terminate()

}






