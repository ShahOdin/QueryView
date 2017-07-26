package com.shah.persistence.demo.account

import akka.actor.ActorLogging
import akka.persistence.fsm._
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.reflect._

object Account {

  val identifier: String = "Account"

  // Account States
  sealed trait State extends FSMState

  case object Empty extends State {
    override def identifier = "Empty"
  }

  case object Active extends State {
    override def identifier = "Active"
  }

  // Account Data
  sealed trait Data {
    val amount: Float
  }

  case object ZeroBalance extends Data {
    override val amount: Float = 0.0f
  }

  case class Balance(override val amount: Float) extends Data

  // Domain Events (Persist events)
  sealed trait DomainEvent

  import com.shah.persistence.demo.AccountApi.TransactionType

  case class AcceptedTransaction(amount: Float,
                                 `type`: TransactionType) extends DomainEvent

  case class RejectedTransaction(amount: Float,
                                 `type`: TransactionType, reason: String) extends DomainEvent

}

class Account extends PersistentFSM[Account.State, Account.Data, Account.DomainEvent]
  with ActorLogging {

  import Account._
  import com.shah.persistence.demo.{AccountApi ⇒ API}

  override def persistenceId: String = identifier

  override def applyEvent(evt: DomainEvent, currentData: Data): Data = {
    evt match {
      case AcceptedTransaction(amount, API.CR) =>
        val newAmount = currentData.amount + amount
        log.info(s"Write side balance: $newAmount")
        Balance(currentData.amount + amount)
      case AcceptedTransaction(amount, API.DR) =>
        val newAmount = currentData.amount - amount
        log.info(s"Write side balance: $newAmount")
        if (newAmount > 0)
          Balance(newAmount)
        else
          ZeroBalance
      case RejectedTransaction(_, _, reason)   =>
        log.info(s"RejectedTransaction with reason: $reason")
        currentData
    }
  }

  override def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]

  startWith(Empty, ZeroBalance)

  when(Empty) {
    case Event(API.Operation(amount, API.CR), _) =>
      goto(Active) applying AcceptedTransaction(amount, API.CR)
    case Event(API.Operation(amount, API.DR), _) =>
      stay applying RejectedTransaction(amount, API.DR, "Balance is Zero")
  }

  when(Active) {
    case Event(API.Operation(amount, API.CR), _)       =>
      stay applying AcceptedTransaction(amount, API.CR)
    case Event(API.Operation(amount, API.DR), balance) =>
      val newBalance = balance.amount - amount
      if (newBalance > 0) {
        stay applying AcceptedTransaction(amount, API.DR)
      }
      else if (newBalance == 0) {
        goto(Empty) applying AcceptedTransaction(amount, API.DR)
      }
      else
        stay applying RejectedTransaction(amount, API.DR, "balance doesn't cover this operation.")
  }

}