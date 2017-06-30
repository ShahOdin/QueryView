package com.shah.persistence.query.model

import akka.actor.Actor
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.scaladsl.Source

trait LeveldBQuerySupport extends Actor{

  def queryJournal(idToQuery: String, fromSequenceNr: Long = 0L,
                   toSequenceNr: Long = Long.MaxValue):
  Source[EventEnvelope, Unit] = {
        PersistenceQuery(context.system).
        readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier).
        eventsByPersistenceId(idToQuery, fromSequenceNr, toSequenceNr)
  }

  def queryJournalFrom(idToQuery: String, fromSequenceNr: Long = 0L)
  :Source[EventEnvelope, Unit] = queryJournal(idToQuery, fromSequenceNr, Long.MaxValue)
}
