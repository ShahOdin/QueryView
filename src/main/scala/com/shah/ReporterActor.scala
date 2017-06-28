package com.shah

import akka.actor.Props
import com.shah.Account._
import com.shah.model.{LeveldBQuerySupport, PersistenceQueryView, Snapshottable}

case class AccountData( override var cache: Float,
                        override var offset: Long= 0L) extends Snapshottable[Float]

class ReporterActor(override val snapshotFrequency:Int)
  extends PersistenceQueryView[DomainEvent, Float, AccountData]
    with LeveldBQuerySupport{

  override def persistenceId: String = ReporterActor.identifier
  override val persistenceIdtoQuery: String = Account.identifier

  override var cachedData = AccountData(0F)

  def updateCache(evt: DomainEvent): Unit = {
    evt match {
      case AcceptedTransaction(amount, CR) ⇒
        cachedData.cache += amount
      case AcceptedTransaction(amount, DR) ⇒
        val newAmount = cachedData.cache - amount
        if (newAmount > 0)
          cachedData.cache = newAmount
      case RejectedTransaction(_, _, _) ⇒ //nothing
    }
    bookKeeping()
    println(s"Read  side balance: $cachedData")// , offset: $journalEventOffset")
  }

  override val receiveReadCommand: Receive = Map.empty
}
object ReporterActor {
  def props() = Props(new ReporterActor(3))

  val identifier: String = "ReporterActor"
}
