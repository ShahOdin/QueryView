package com.shah.persistence.demo

import akka.actor.Props
import akka.persistence.PersistentActor
import com.shah.persistence.demo.Account._
import com.shah.persistence.query.model.{LeveldBQuerySupport, QueryView}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object AccountViewApi{
  case object ReadAccountBalance
}

class AccountView


object AccountView {
  val API= AccountViewApi
  def props(_snapshotFrequency: Int)
           (implicit dataCT: ClassTag[Float],
            _ec: ExecutionContext)
    = Props(
    new PersistentActor with QueryView[Float] with LeveldBQuerySupport {

      override def viewId: String = AccountView.identifier
      override def queryId: String = Account.identifier

      override val snapshotFrequency: Int = _snapshotFrequency
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

      override implicit val ec: ExecutionContext = _ec
      override implicit val data = dataCT
    }
  )

  val identifier: String = "ReporterActor"
}


