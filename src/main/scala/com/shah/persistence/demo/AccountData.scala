package com.shah.persistence.demo

import com.shah.persistence.query.model.QueryViewData

case class AccountData( override var cache: Float,
                        override var offsetForNextFetch: Long= 1L) extends QueryViewData{
  type Data = Float
}
