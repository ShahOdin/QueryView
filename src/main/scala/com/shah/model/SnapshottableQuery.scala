package com.shah.model

trait SnapshottableQuery[D] {
  var offset: Long
  var cache: D
}