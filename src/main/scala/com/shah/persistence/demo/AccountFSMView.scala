package com.shah.persistence.demo

import akka.actor.Props
import akka.stream.ActorMaterializer
import com.shah.persistence.demo.Account.{Empty, ZeroBalance}
import com.shah.persistence.demo.AccountView.API
import com.shah.persistence.query.model._

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

class AccountFSMViewImpl(override val snapshotFrequency: Int)
                     (implicit override val ec: ExecutionContext)
  extends QueryViewFSMImpl[Account.State, Account.Data, Account.DomainEvent] with LeveldBQuerySupport {

  val materializer = ActorMaterializer()
  override var cachedData: Account.Data = ZeroBalance

  def viewId: String = AccountFSMView.identifier

  def queryId: String = Account.identifier

  override def handleReads: Receive ={
    case API.ReadAccountBalance ⇒
      println(s"Account balance: $cachedData")
  }

  startWith(Empty, ZeroBalance)
}

object AccountFSMView {
  def props(snapshotFrequency: Int)(implicit data: ClassTag[Account.Data], ec: ExecutionContext)
  = Props(new AccountFSMViewImpl(snapshotFrequency))

  val identifier: String = "AccountFSMView"
}


