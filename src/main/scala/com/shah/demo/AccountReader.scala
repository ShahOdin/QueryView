package com.shah.demo

import akka.actor.Props
import com.shah.demo.Account._
import com.shah.model.query.{LeveldBQuerySupport, QueryView}

case object ReadAccountBalance

class AccountReader(override val snapshotFrequency:Int) extends QueryView[AccountData]
    with LeveldBQuerySupport{

  override def persistenceId: String = AccountReader.identifier
  override val persistenceIdtoQuery: String = Account.identifier

  override var cachedData = AccountData(0F)

  override val receiveReadCommand: Receive = {
    case ReadAccountBalance ⇒
      println(s"Account balance: ${cachedData.cache}")
  }

  override def updateCache: Receive = {
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
}
object AccountReader {
  def props() = Props(new AccountReader(3))

  val identifier: String = "ReporterActor"
}
