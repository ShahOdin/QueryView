package com.shah

import akka.actor.{ActorSystem, Props}
import com.shah.Account.{CR, DR, Operation}


object ReporterApp extends App {

  val system: ActorSystem = ActorSystem("persistent-query")

  val account = system.actorOf(Props[Account])

  val repoter = system.actorOf(ReporterActor.props())


  account ! Operation(7000, CR)
  account ! Operation(700, DR)

  Thread.sleep(5000)

  system.terminate()

}