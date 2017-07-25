package com.shah.persistence.demo.account

import akka.actor.Props
import akka.stream.ActorMaterializer
import com.shah.persistence.demo.account
import com.shah.persistence.query.model.{LeveldBQuerySupport, QueryViewBase, QueryViewImpl}

import scala.concurrent.ExecutionContext

class AccountView extends QueryViewBase[Float] {

  import Account._
  import com.shah.persistence.demo.{AccountViewApi ⇒ API}

  def viewId: String = AccountView.identifier

  def queryId: String = Account.identifier

  def receiveReads: Receive = {
    case API.PrintAccountBalance ⇒
      println(s"Account balance: $balance")

    case API.ReturnAccountBalance ⇒
      sender() ! balance
  }

  import com.shah.persistence.demo.AccountApi._

  def receiveJournalEvents: Receive = {
    case AcceptedTransaction(amount, CR) ⇒
      balance += amount
      println(s"+Read  side balance: $balance")
    case AcceptedTransaction(amount, DR) ⇒
      val newAmount = balance - amount
      if (newAmount > 0)
        balance = newAmount
      println(s"-Read  side balance: $balance")

    case RejectedTransaction(_, _, _) ⇒
  }

  var balance: Float = 0L

  def saveSnapshot(): Unit = {
    saveSnapshot(balance)
  }

  def applySnapshot(updatedBalance: Float) = {
    balance = updatedBalance
  }
}

class AccountViewImpl(val snapshotFrequency: Int)(implicit override val ec: ExecutionContext)
  extends AccountView with QueryViewImpl with LeveldBQuerySupport

object AccountView {

  def props(snapshotFrequency: Int)(implicit ec: ExecutionContext) =
    Props(new AccountViewImpl(snapshotFrequency))

  val identifier: String = "AccountView"
}


