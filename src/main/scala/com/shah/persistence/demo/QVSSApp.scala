package com.shah.persistence.demo

import akka.actor.ActorSystem
import com.shah.persistence.query.model.QVSApi

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global

//tests the sequence snapshotter!
object QVSSApp extends App{
  val system: ActorSystem = ActorSystem("SequenceSnapshotting")

  val snapshotter = system.actorOf(QVSApi.props("xxx",3))
  implicit val timeout = Timeout(5 seconds)

  (snapshotter ? QVSApi.GetLastSnapshottedSequenceNr).mapTo[Long].map(l ⇒ println("sequence: "+ l))

  snapshotter ! QVSApi.IncrementFromSequenceNr
  snapshotter ! QVSApi.IncrementFromSequenceNr
  snapshotter ! QVSApi.IncrementFromSequenceNr
  snapshotter ! QVSApi.IncrementFromSequenceNr
  snapshotter ! QVSApi.IncrementFromSequenceNr
  snapshotter ! QVSApi.IncrementFromSequenceNr
  snapshotter ! QVSApi.IncrementFromSequenceNr


  (snapshotter ? QVSApi.GetLastSnapshottedSequenceNr).mapTo[Long].map(l ⇒ println("sequence: "+ l))

  Thread.sleep(3000)
  system.terminate()
}
