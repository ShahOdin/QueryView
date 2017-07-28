package com.shah.persistence.demo

object TestUtils {

  object Mock {

    import akka.actor.Props
    import akka.stream.ActorMaterializer
    import com.shah.persistence.query.model.QueryViewImpl

    import scala.concurrent.ExecutionContext

    object AccountView {

      import akka.actor.{Actor, ActorSystem}
      import com.shah.persistence.demo.account.AccountView
      import com.shah.persistence.query.model.ReadJournalQuerySupport

      trait InMemQuerySupport extends ReadJournalQuerySupport with Actor {

        import akka.NotUsed
        import akka.persistence.query.{EventEnvelope, PersistenceQuery}
        import akka.stream.scaladsl.Source
        import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal

        def queryJournal(idToQuery: String, fromSequenceNr: Long = 0L,
                         toSequenceNr: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] = {
          PersistenceQuery(context.system).
            readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier).
            eventsByPersistenceId(idToQuery, fromSequenceNr, toSequenceNr)
        }

        override def queryJournalFrom(idToQuery: String, fromSequenceNr: Long = 0L): Source[EventEnvelope, NotUsed] =
          queryJournal(idToQuery, fromSequenceNr, Long.MaxValue)
      }

      class AccountViewMockImpl(val snapshotFrequency: Int,
                                val streamParallelism: Int)
                               (implicit override val ec: ExecutionContext)
        extends AccountView with QueryViewImpl with InMemQuerySupport

      def props(snapshotFrequency: Int)(implicit ec: ExecutionContext) =
        Props(new AccountViewMockImpl(snapshotFrequency, 2))

      def startActor(system: ActorSystem, snapshotFrequency: Int)
                    (implicit ec: ExecutionContext) = system.actorOf(props(snapshotFrequency))

    }

  }

}