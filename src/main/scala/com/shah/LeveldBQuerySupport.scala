package com.shah

import akka.actor.Actor
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.scaladsl.Source

trait LeveldBQuerySupport extends Actor{

  val queryJournalForPersistentId:
  (String, Long) => Source[EventEnvelope, Unit]= {
      (idToQuery:String, queryOffset:Long) ⇒
        PersistenceQuery(context.system).
        readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier).
        eventsByPersistenceId(idToQuery,queryOffset)
  }

}
