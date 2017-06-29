package com.shah.demo

import akka.actor.Props
import com.shah.demo.Account._
import com.shah.model.query.{LeveldBQuerySupport, QueryView, SnapshottableQuerriedData}

case class AccountData( override var cache: Float,
                        override var offsetForNextFetch: Long= 1L) extends SnapshottableQuerriedData[Float]

class AccountReader(override val snapshotFrequency:Int)
  extends QueryView[DomainEvent, Float, AccountData]
    with LeveldBQuerySupport{

  override def persistenceId: String = AccountReader.identifier
  override val persistenceIdtoQuery: String = Account.identifier

  override var cachedData = AccountData(0F)

  def updateCache(evt: DomainEvent): Unit = {
    evt match {
      case AcceptedTransaction(amount, CR) ⇒
        cachedData.cache += amount
        println(s"+Read  side balance: ${cachedData.cache}")
      case AcceptedTransaction(amount, DR) ⇒
        val newAmount = cachedData.cache - amount
        if (newAmount > 0)
          cachedData.cache = newAmount
        println(s"-Read  side balance: ${cachedData.cache}")
      case RejectedTransaction(_, _, _) ⇒ //nothing
    }
    bookKeeping()
  }

  override val receiveReadCommand: Receive = Map.empty
}
object AccountReader {
  def props() = Props(new AccountReader(3))

  val identifier: String = "ReporterActor"
}
