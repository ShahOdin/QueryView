package com.shah.persistence.demo

import akka.actor.Props
import akka.stream.ActorMaterializer
import com.shah.persistence.demo.Account._
import com.shah.persistence.query.model.{LeveldBQuerySupport, QueryViewBase, QueryViewImpl}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object AccountViewApi {

  case object PrintAccountBalance

  case object ReturnAccountBalance

}

class AccountView extends QueryViewBase[Float] {

  import AccountView._

  def viewId: String = AccountView.identifier

  def queryId: String = Account.identifier

  def receiveReads: Receive = {
    case API.PrintAccountBalance ⇒
      println(s"Account balance: $balance")

    case API.ReturnAccountBalance ⇒
      sender() ! balance
  }

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
  extends AccountView with QueryViewImpl with LeveldBQuerySupport {
  val materializer = ActorMaterializer()
}

object AccountView {
  val API = AccountViewApi

  def props(snapshotFrequency: Int)(implicit ec: ExecutionContext) =
    Props(new AccountViewImpl(snapshotFrequency))

  val identifier: String = "AccountView"
}


