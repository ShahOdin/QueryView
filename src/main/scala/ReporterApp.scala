package com.packt.akka

import akka.actor.{ActorSystem, Props}
import com.packt.akka.Account.{CR, DR, Operation}


object ReporterApp extends App {

  val system: ActorSystem = ActorSystem("persistent-query")

  val account = system.actorOf(Props[Account])

  val repoter = system.actorOf(ReporterActor.props())


  account ! Operation(7000, CR)
  account ! Operation(700, DR)

  Thread.sleep(10000)

  system.terminate()

}