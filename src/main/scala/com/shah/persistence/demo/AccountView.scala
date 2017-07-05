package com.shah.persistence.demo

import akka.actor.{Actor, Props}
import akka.persistence.PersistentActor
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

  def handleReads: Receive = {
    case API.ReadAccountBalance ⇒
      println(s"Account balance: $cachedData")
  }

  def updateCache: Receive = {
    case AcceptedTransaction(amount, CR) ⇒
      cachedData += amount
      println(s"+Read  side balance: $cachedData")
    case AcceptedTransaction(amount, DR) ⇒
      val newAmount = cachedData - amount
      if (newAmount > 0)
        cachedData = newAmount
      println(s"-Read  side balance: $cachedData")

    case RejectedTransaction(_, _, _) ⇒
  }

  def receiveCommand: Receive = updateCache orElse handleReads

  override type Data = Float
  var cachedData: Float = 0L
}

class AccountViewImpl(override val snapshotFrequency: Int)(implicit override val ec: ExecutionContext)
  extends AccountView with QueryViewImpl with LeveldBQuerySupport {
  val materializer = ActorMaterializer()
}

object AccountView {
  val API = AccountViewApi

  def props(snapshotFrequency: Int)(implicit ec: ExecutionContext) =
    Props(new AccountViewImpl(snapshotFrequency))

  val identifier: String = "AccountView"
}


