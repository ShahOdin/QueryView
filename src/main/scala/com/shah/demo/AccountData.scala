package com.shah.demo

import com.shah.model.query.SnapshottableQuerriedData

case class AccountData( override var cache: Float,
                        override var offsetForNextFetch: Long= 1L) extends SnapshottableQuerriedData{
  type Data = Float
}
