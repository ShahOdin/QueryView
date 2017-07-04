package com.shah.persistence.query.model

import akka.persistence.PersistentActor
import akka.actor.ActorRef
import akka.persistence.{RecoveryCompleted, SnapshotOffer}
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import akka.pattern.ask
import akka.stream.ActorMaterializer
import com.shah.persistence.query.model.QVSSnapshotter.API

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Success}
import scala.concurrent.duration._

//Persistent Actors can mix-in this trait to specify the main logic of the read actor.
//the client should not have to specify the receiveRecover block.
trait QueryViewBase extends PersistentActor with QueryViewInfo{
  def receiveRecover: Receive = Map.empty
}

//The view actors need to mix-in this to get the pipelines working together.
trait QueryViewImpl[D] extends QueryViewImplBase[D]{

  override def receiveRecover: Receive = receiveQueryViewSnapshot

  abstract override def receiveCommand: Receive = {
    QueryViewCommandPipeline andThen (super.receiveCommand orElse unhandledCommand)
  }

}
