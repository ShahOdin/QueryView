package com.shah.persistence.demo

import com.shah.persistence.query.model.SnapshottableQuerriedData

case class AccountData( override var cache: Float,
                        override var offsetForNextFetch: Long= 1L) extends SnapshottableQuerriedData{
  type Data = Float
}
