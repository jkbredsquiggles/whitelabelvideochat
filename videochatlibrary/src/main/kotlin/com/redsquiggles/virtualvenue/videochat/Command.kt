package com.redsquiggles.virtualvenue.videochat

import com.redsquiggles.communications.bus.Context

interface ServiceMessage {
    val context: Context
}

data class User(val id: String, val userName: String)

data class ConnectWith(override val context: Context, val requestedBy : User, val otherParties: List<User>) : ServiceMessage

