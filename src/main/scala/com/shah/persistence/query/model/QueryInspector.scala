package com.shah.persistence.query.model

import akka.actor.{Actor, ActorLogging}
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

case class PrintEvents(persistenceId: String,
                       fromSequenceNr: Long = 0L,
                       toSequenceNr: Long = Long.MaxValue)

trait QueryInspector extends Actor with ActorLogging {

  import akka.NotUsed

  implicit val materializer = ActorMaterializer()

  def queryJournal(idToQuery: String, fromSequenceNr: Long,
                   toSequenceNr: Long): Source[EventEnvelope, NotUsed]

  override def receive: Receive = {
    case PrintEvents(id, from, to) ⇒
      queryJournal(id, from, to).runWith(Sink.actorRef(self, None))

    case evt: EventEnvelope ⇒ log.info(s"event: $evt")
  }
}
