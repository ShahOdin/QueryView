package com.shah.persistence.demo

import akka.actor.Props
import akka.persistence.PersistentActor
import akka.stream.ActorMaterializer
import com.shah.persistence.demo.Account._
import com.shah.persistence.query.model.{LeveldBQuerySupport, QueryViewBase, QueryViewImpl}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object AccountViewApi{
  case object ReadAccountBalance
}

class AccountView
  extends PersistentActor with QueryViewBase{
  import AccountViewImpl._

  def viewId: String = AccountViewImpl.identifier
  def queryId: String = Account.identifier

  var cachedData: Float = 0L

  val materializer = ActorMaterializer()

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

    case RejectedTransaction(_, _, _) ⇒
  }

  def receiveCommand: Receive = updateCache orElse handleReads
}

class AccountViewImpl(override val snapshotFrequency: Int)
                     (implicit override val data: ClassTag[Float], override val ec: ExecutionContext)
  extends AccountView with QueryViewImpl[Float] with LeveldBQuerySupport {
}

object AccountViewImpl {
  val API= AccountViewApi

  def props(snapshotFrequency: Int)(implicit data: ClassTag[Float], ec: ExecutionContext)
  =  Props(new AccountViewImpl(snapshotFrequency))

  val identifier: String = "AccountView"
}


