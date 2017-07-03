package com.shah.persistence.demo

import akka.actor.Props
import akka.persistence.PersistentActor
import com.shah.persistence.demo.Account._
import com.shah.persistence.demo.AccountView.API
import com.shah.persistence.query.model.{LeveldBQuerySupport, QueryView, QueryViewBase}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object AccountViewApi{
  case object ReadAccountBalance
}

class AccountView(val snapshotFrequency: Int)
                          (implicit val data: ClassTag[Float],
                  val ec: ExecutionContext)
  extends QueryViewBase[Float] with LeveldBQuerySupport{

  def viewId: String = AccountView.identifier
  def queryId: String = Account.identifier

  var cachedData: Float = 0L

  def handleReads: Receive ={
    case API.ReadAccountBalance ⇒
      println(s"Account balance: $cachedData")
  }

  def updateCache: Receive ={
    case AcceptedTransaction(amount, CR) ⇒
      cachedData += amount
      println(s"+Read  side balance: $cachedData")
    case AcceptedTransaction(amount, DR) ⇒
      val newAmount = cachedData - amount
      if (newAmount > 0)
        cachedData = newAmount
      println(s"-Read  side balance: $cachedData")

    case RejectedTransaction(_, _, _) ⇒ //nothing
  }

  def receiveCommand: Receive = handleReads orElse updateCache

  override def receiveRecover: Receive = Map.empty
}

class AccountViewImplementation(snapshotFrequency: Int)
                               (implicit data: ClassTag[Float],
                                ec: ExecutionContext) extends
  AccountView(snapshotFrequency) with QueryView[Float]

object AccountView {
  val API= AccountViewApi

  def props(snapshotFrequency: Int)(implicit data: ClassTag[Float], ec: ExecutionContext)
  =  Props(new AccountViewImplementation(snapshotFrequency))

  val identifier: String = "AccountView"
}


