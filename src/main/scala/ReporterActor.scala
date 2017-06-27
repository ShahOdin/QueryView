package com.packt.akka

import akka.actor.Props
import com.packt.akka.Account.{DomainEvent, _}

class ReporterActor(override val snapshotFrequency:Int)
  extends PersistenceQueryView[DomainEvent,Float]
    with LeveldBQuerySupport{

  override def persistenceId: String = ReporterActor.persistenceId
  override val persistenceIdtoQuery: String = Account.persistenceId

  override var cachedData: Float = 0f
  def updateCache(evt: DomainEvent): Unit = {
    evt match {
      case AcceptedTransaction(amount, CR) ⇒
        cachedData += amount
      case AcceptedTransaction(amount, DR) ⇒
        val newAmount = cachedData - amount
        if (newAmount > 0)
          cachedData = newAmount
      case RejectedTransaction(_, _, _) ⇒ //nothing
    }
    bookKeeping()
    println(s"after operation, cache: $cachedData , offset: $journalEventOffset")
  }

  override val receiveReadCommand: Receive = Map.empty
}
object ReporterActor {
  def props() = Props(new ReporterActor(3))

  val persistenceId: String = "AccountReporteR"
}
