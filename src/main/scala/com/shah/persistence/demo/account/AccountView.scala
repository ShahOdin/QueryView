package com.shah.persistence.demo.account

import akka.actor.ActorLogging
import com.shah.persistence.query.model.{LeveldBQuerySupport, QueryViewBase, QueryViewImpl}

import scala.concurrent.ExecutionContext

class AccountView extends QueryViewBase[Float] with ActorLogging {

  import com.shah.persistence.demo.{AccountViewApi ⇒ API}

  def viewId: String = AccountView.identifier

  def queryId: String = Account.identifier

  def receiveReads: Receive = {
    case API.PrintAccountBalance ⇒
      log.debug(s"Account balance: $balance")

    case API.ReturnAccountBalance ⇒
      sender() ! balance
  }

  import com.shah.persistence.demo.AccountApi
  import Account.{AcceptedTransaction, RejectedTransaction}

  def receiveJournalEvents: Receive = {
    case AcceptedTransaction(amount, AccountApi.CR) ⇒
      balance += amount

      log.info(s"+Read side balance: $balance")
    case AcceptedTransaction(amount, AccountApi.DR) ⇒
      val newAmount = balance - amount
      if (newAmount > 0)
        balance = newAmount
      log.info(s"-Read side balance: $balance")

    case RejectedTransaction(_, _, _) ⇒
  }

  var balance: Float = 0L

  def saveSnapshot(): Unit = {
    log.info(s"snapshotted with balance: $balance")
    saveSnapshot(balance)
  }

  def applySnapshot(updatedBalance: Float) = {
    balance = updatedBalance
  }
}

class AccountViewImpl(val snapshotFrequency: Int,
                      val streamParallelism: Int)
                     (implicit override val ec: ExecutionContext)
  extends AccountView with QueryViewImpl with LeveldBQuerySupport

object AccountView {

  val identifier: String = "AccountView"
}


