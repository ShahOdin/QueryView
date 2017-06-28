package com.shah

import akka.actor.Props
import com.shah.Account._

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
    println(s"Read  side balance: $cachedData")// , offset: $journalEventOffset")
  }

  override val receiveReadCommand: Receive = Map.empty
}
object ReporterActor {
  def props() = Props(new ReporterActor(3))

  val persistenceId: String = "ReporterActor"
}
