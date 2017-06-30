package com.shah.persistence.query.model

//  not defaulting offsetForNextFetch here as the case classes mixing in this trait
// are advised to override it as one of their fields for potential equality purposes.

trait QueryViewData {
  var offsetForNextFetch: Long //= 1L

  type Data
  var cache: Data
}


