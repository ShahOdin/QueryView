package com.shah.persistence.demo

import akka.actor.Props
import akka.persistence.PersistentActor
import com.shah.persistence.demo.Account._
import com.shah.persistence.demo.AccountView.API
import com.shah.persistence.query.model.{LeveldBQuerySupport, QueryView}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object AccountViewApi{
  case object ReadAccountBalance
}

class AccountView(override val snapshotFrequency: Int)
                 (implicit override val data: ClassTag[Float],
                  override val ec: ExecutionContext)
  extends PersistentActor() with QueryView[Float] with LeveldBQuerySupport {

  override def viewId: String = AccountView.identifier
  override def queryId: String = Account.identifier

  override var cachedData: Float = 0L

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

  override def receiveCommand: Receive = handleReads orElse updateCache
}

object AccountView {
  val API= AccountViewApi
  def props(snapshotFrequency: Int)
           (implicit data: ClassTag[Float],
            ec: ExecutionContext)
  =  Props(new AccountView(snapshotFrequency))

  val identifier: String = "AccountView"
}


