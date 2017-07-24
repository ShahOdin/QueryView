package com.shah.persistence.query.model

trait ReadJournalQuerySupport {

  import akka.NotUsed
  import akka.persistence.query.EventEnvelope
  import akka.stream.scaladsl.Source

  def queryJournalFrom(idToQuery: String, fromSequenceNr: Long = 0L): Source[EventEnvelope, NotUsed]
}
