package com.redsquiggles.virtualvenue.videochat

interface VideoChatService {
    fun process(command: ConnectWith)
}

class VideoChatServiceImpl : VideoChatService {
    override fun process(command: ConnectWith) {
        TODO("Not yet implemented")
        // Create an approriate request for Jitsi with the JWT token to create a chat session
        // Send the request to Jistsi
        // Send a VideoChatRequest to the recipients with the session details from the response
        // Send the same to the sender (sender is in a state that will know how to deal with it) OR
        // send a different message so sender knows that video is active and recipients are notified

    }

    // Will need to work out decline logic - given that the session may host many participants,
    // that may take a while to join. It may be that the decline will only mean anything as
    // a way of notifying the host that ALL other particpants have declined.


}