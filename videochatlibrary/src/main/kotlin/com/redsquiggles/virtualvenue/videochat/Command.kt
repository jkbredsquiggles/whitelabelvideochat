package com.redsquiggles.virtualvenue.videochat

import com.redsquiggles.communications.bus.Context


// inbound command/event
interface ServiceMessage {
    val context: Context
}

data class VideoChatUser(val id: String, val name: String)

/**
 * Command to process the fact that a user has connected to the bus
 */
data class ConnectUser(override val context: Context, val user : VideoChatUser) : ServiceMessage


/**
 * Update information for the specified existing + connected user
 */
data class UpdateUser(override val context: Context, val user : VideoChatUser) : ServiceMessage


/**
 * Command to process the fact that the user associated with the supplied connection string has disconnected
 */
data class DisconnectUser(override val context: Context) : ServiceMessage

data class StartVideoChatWith(override val context: Context, val requestedById : String, val otherPartyIds: List<String>) : ServiceMessage

// outbound command/event

data class NewConnection(
    var user: VideoChatUser
)

data class UserList(
    var users: List<VideoChatUser>
)

/**
 * Server to client event, notifies that a user has disconnected
 */
data class Disconnected(
    var userId : String
)

enum class Error {
    NotAuthenticated,
    InternalError,
    BadRequest,
    NotAuthorized
}

data class ErrorMessage(
    var error: Error
)


data class VideoChatDetails(var sessionId: String, var authenticationToken: String, var hostId: String)