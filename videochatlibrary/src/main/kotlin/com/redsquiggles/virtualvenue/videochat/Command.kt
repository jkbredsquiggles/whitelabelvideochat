package com.redsquiggles.virtualvenue.videochat

import com.redsquiggles.communications.bus.Context

interface ServiceMessage {
    val context: Context
}

data class ConnectWith(override val context: Context, val userId: List<String>) : ServiceMessage

