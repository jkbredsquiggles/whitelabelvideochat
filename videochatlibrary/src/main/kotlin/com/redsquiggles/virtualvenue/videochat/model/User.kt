package com.redsquiggles.virtualvenue.videochat.model

import java.time.Instant

data class UserPaginationKey( val id: String)

//data class User (val id : String, val name: String, val created: Instant, val lastConnected: Instant,
//                 val connectionId: String?,
//                 val locations: Set<String>)
data class User (val id : String, val name: String, val created: Instant, val lastConnected: Instant,
                 val connectionId: String?)
