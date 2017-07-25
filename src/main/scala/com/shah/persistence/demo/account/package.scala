package com.shah.persistence.demo


package object AccountApi {

  // Transaction Types
  private[demo] sealed trait TransactionType

  case object CR extends TransactionType

  case object DR extends TransactionType

  // Commands
  case class Operation(amount: Float, `type`: TransactionType)
}

package object AccountViewApi {

  case object PrintAccountBalance

  case object ReturnAccountBalance

}