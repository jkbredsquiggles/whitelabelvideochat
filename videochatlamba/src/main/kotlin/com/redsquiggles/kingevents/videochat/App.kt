package com.redsquiggles.kingevents.videochat

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.redsquiggles.communications.bus.Error
import com.redsquiggles.communications.bus.Message
import com.redsquiggles.communications.bus.MessageBus
import com.redsquiggles.communications.bus.Response
import com.redsquiggles.utilities.Result
import com.redsquiggles.virtualvenue.websocketlambda.APIGatewayBridge
import com.redsquiggles.virtualvenue.websocketlambda.ApiGatewayWebSocketBridgeServiceImpl
import com.redsquiggles.virtualvenue.websocketlambda.EventAndContext

class App : ApiGatewayWebSocketBridgeServiceImpl() {
    override fun authorize(messageAsJson: JsonObject): Result<Boolean> {
        TODO("Not yet implemented")
    }

    override fun init(eventAndContext: EventAndContext) {
        TODO("Not yet implemented")
    }

    override fun processAuthorized(messageAsJson: JsonObject): APIGatewayV2WebSocketResponse {
        TODO("Not yet implemented")
    }

    override fun processWebSocketDisconnect(connectionId: String) {
        TODO("Not yet implemented")
    }

}

// outbound messages - server to client

// These need to be defined in the video chat library
/**
 * Destination has both the chat User Id and the connectionId to simplify and improve efficiency for dealing
 * with fallout from failures sending messages - e.g. to disconnect the message recipient
 */
data class Destination( var id: String, var connectionId : String)

interface Destinations {
    var  destinations : List<Destination>
}

data class Envelope(var messageId: String, var inReplyTo: String?, override var destinations: List<Destination>, var content: ServerToClientMessageContent) : Destinations, Message
data class Disconnect(var connectionId: String) : Message

sealed class VideoChat

sealed class ServerToClientMessageContent

// inbound messages client to server

interface ClientToServerMessageContent

data class ConnectWith(var userIds: List<String>)  : ClientToServerMessageContent


// end of section that shold be moved to vidochatlibrary

class VideoChatMessageBus(val gson: Gson, val apiGatewayBridge: APIGatewayBridge) : MessageBus {

    data class  VideChatMessage(var messageId : String, var inReplyTo: String?, var type: String, var content: Any) {
        constructor(messageId: String, inResponseTo: String?, value: Any) : this(messageId,inResponseTo,value::class.simpleName!!,value)
    }

    fun Envelope.serialize(): String {
        return gson.toJson(VideChatMessage(this.messageId,this.inReplyTo,this.content))
    }


    override fun process(command: Message): com.redsquiggles.communications.bus.Result<Response> {
        var transformedCommand  = when (command) {
            is Envelope -> command
            is Disconnect -> {
                apiGatewayBridge.disconnectClient(command.connectionId)
                return com.redsquiggles.communications.bus.Result.success(Response.CommandSent)
            }
            else -> return com.redsquiggles.communications.bus.Result.failure<Response>(Error.CommandNotSent)
        }
        val serializedMessage = transformedCommand.serialize()
        val errors  = transformedCommand.destinations.mapNotNull {
            try {
                apiGatewayBridge.sendMessageToClient(it.connectionId, serializedMessage)
                null
            } catch(e: Throwable) {
                System.out.println(e.message)
                it
            }
        }
        return if (errors.count() > 0) {
            com.redsquiggles.communications.bus.Result.failure(Error.CommandNotSentToSomeDestinations(errors))
        } else {
            com.redsquiggles.communications.bus.Result.success(Response.CommandSent)
        }

    }

}