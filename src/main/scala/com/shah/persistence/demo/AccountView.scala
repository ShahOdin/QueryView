package com.shah.persistence.demo

import akka.actor.Props
import akka.stream.ActorMaterializer
import com.shah.persistence.demo.Account._
import com.shah.persistence.query.model.{LeveldBQuerySupport, QueryViewBase, QueryViewImpl}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object AccountViewApi {

  case object ReadAccountBalance

}

class AccountView(implicit val data: ClassTag[Float]) extends QueryViewBase {

  import AccountView._

  def viewId: String = AccountView.identifier

  def queryId: String = Account.identifier

  def receiveReads: Receive = {
    case API.ReadAccountBalance ⇒
      println(s"Account balance: $cachedData")
  }

  def receiveJournalEvents: Receive = {
    case AcceptedTransaction(amount, CR) ⇒
      balance += amount
      println(s"+Read  side balance: $cachedData")
    case AcceptedTransaction(amount, DR) ⇒
      val newAmount = cachedData - amount
      if (newAmount > 0)
        balance = newAmount
      println(s"-Read  side balance: $cachedData")

    case RejectedTransaction(_, _, _) ⇒
  }

  override type Data = Float
  var balance: Float = 0L

  override def updateCachedData(updatedBalance: Float) = {
    balance = updatedBalance
  }
  override def cachedData: Float = balance
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


