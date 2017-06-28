package com.shah.model

trait Snapshottable[D] {
  var offset: Long
  var cache: D
}