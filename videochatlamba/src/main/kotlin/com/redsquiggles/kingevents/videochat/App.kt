package com.redsquiggles.kingevents.videochat

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse
import com.google.gson.*
import com.redsquiggles.communications.bus.*
import com.redsquiggles.utilities.Result
import com.redsquiggles.virtualvenue.videochat.ServiceMessage
import com.redsquiggles.virtualvenue.websocketlambda.*
import com.redsquiggles.virtualvenue.websocketlambda.BusCommand
import com.redsquiggles.virtualvenue.websocketlambda.Disconnect
import java.util.*

// Video chat lambda
class App : ApiGatewayWebSocketBridgeServiceImpl() {
    lateinit  var clientSerializationUtilities: VideoChatClientSerialization
    lateinit var messageHelper : APIGatewayMessageUtilities
    override fun authorize(connectionId: String,messageAsJson: JsonObject): Result<Boolean> {
        // Need to perform token authorization of the ConnectWith message
        // otherwise, authorize
        val message = messageHelper.deserializeBusMessage(connectionId,messageAsJson,
        clientSerializationUtilities::deserializeBusMessageValue)
        return if (message.message.content is ConnectWith) {
            jwtAuthorize(messageAsJson)
        } else {
            Result.success(true)
        }

    }

    override fun init(eventAndContext: EventAndContext) {
        this.clientSerializationUtilities = VideoChatClientSerialization(gson)
        this.messageBus = APIGatewayToVideoChatMessageBus(gson,
            this.webSocketBridge,
            clientSerializationUtilities,
        VideoChatServiceSerialization(gson))
        messageHelper
    }

    override fun processAuthorized(connectionId: String, messageAsJson: JsonObject) {

        // transform message
        val message = messageHelper.deserializeBusMessage(connectionId,messageAsJson,
            clientSerializationUtilities::deserializeBusMessageValue)
        val serviceMessage = clientSerializationUtilities.transformToServiceMessage<ServiceMessage>(message)
        val transformedRequest = BusMessage(message.message.messageId,message.message.inReplyTo,serviceMessage)
        val envelope = BusMessageEnvelope(connectionId,transformedRequest)
        messageBus.processInbound(envelope)

    }

    override fun processWebSocketConnect(connectionId: String) {
        throw NotImplementedError("Don't think this will be required.")
    }

    override fun processWebSocketDisconnect(connectionId: String) {
    // This is what would be done if wireing up the chat server
        // Video chat server doesn't support this just yet
        // So we currently do nothing.
    //        val context = Context(UUID.randomUUID().toString(), connectionId)
//        val command = DisconnectUser(context)
//        server.process(command)
    }

}

// outbound messages - server to client

// These need to be defined in the video chat library or services library




// end of section that should be moved to vidochatlibrary


interface VideoChatMessage
data class ConnectWith(var userIds: List<String>)  : ClientToServerMessageContent, VideoChatMessage




//// Each application implements its own implementation for transforming BusMessageEnevelop content to
//// the messages required by the service
//// Each application implements its own method for processing inbound methods - i.e. calling the appropriate service
//abstract class ApiServiceMessageBus(val gson: Gson, val apiGatewayBridge: WebSocketGatewayBridge,
//                                    val clientSerializationUtilities : ClientToBusGsonSerialization,
//                                    val serviceSerializationUtilities: ServiceToBusGsonSerialization
//) : MessageBus,WebSocketServiceMessageBus {
//
//
//    // API Gateway specific parsing function that will be common to most API applications
//    fun deserializeBusMessage(connectionId: String, parsed: JsonObject) : BusMessageEnvelope {
//        val type = gson.fromJson(parsed.get("type"), String::class.java)
//        val messageId = gson.fromJson(parsed.get("messageId"), String::class.java)
//        val inReplyTo = parsed.get("inReplyTo")?.let {
//            gson.fromJson(it, String::class.java)
//        }
//        //val message = gson.fromJson(this, BusMessage::class.javaObjectType)
//        val value = parsed.get("value")
//        val messageContent = clientSerializationUtilities.deserializeBusMessageValue(type,value)
//        val busMessage = BusMessage(messageId,inReplyTo,type,messageContent)
//
//        return BusMessageEnvelope(connectionId,busMessage)
//    }
//
//    // General
//    //abstract fun <T> transformToServiceMessage(busMessage: BusMessageEnvelope) : T
//
//    // This would be implemented by the logic in processFrame in the Ktor ChatApplication, except connect and disconnect handling
//    // would be handled by the bus comment
//    // This would be implemented by the logic in handleWebSocketMessage in the API Gateway Apps.
//    //abstract fun processInbound(command: ClientToServerMessageEnvelope) : com.redsquiggles.communications.bus.Result<Response>
//
//    override fun processOutbound(command: ServerToClientMessageEnvelope) : com.redsquiggles.communications.bus.Result<Response> {
//        val serializedMessage = serviceSerializationUtilities.serialize(command)
//        val errors  = command.destinations.mapNotNull {
//            apiGatewayBridge.sendMessageToClient(it.connectionId, serializedMessage).let {result ->
//                if (result.isFailure) {
//                    null
//                } else {
//                    it
//                }
//            }
//        }
//        return if (errors.count() > 0) {
//            com.redsquiggles.communications.bus.Result.failure(Error.CommandNotSentToSomeDestinations(errors))
//        } else {
//            com.redsquiggles.communications.bus.Result.success(Response.CommandSent)
//        }
//
//    }
//
//    override fun processBusCommand(command: BusCommand) : com.redsquiggles.communications.bus.Result<Response> {
//        when (command) {
//            is Disconnect -> {
//                apiGatewayBridge.process(APIGatewayDisconnect(command.connectionId))
//                return com.redsquiggles.communications.bus.Result.success(Response.CommandSent)
//            }
//            else -> throw NotImplementedError("message type not supported ${command::class.simpleName}")
//        }
//    }
//
//
//    override fun process(command: Message): com.redsquiggles.communications.bus.Result<Response> {
//        var transformedCommand  = when (command) {
//            is ServerToClientMessageEnvelope -> return processOutbound(command)
//            is BusCommand -> return processBusCommand(command)
//            is ClientToServerMessageEnvelope -> return processInbound(command)
//            else -> throw NotImplementedError("message type not supported ${command::class.simpleName}")
//        }
//
//    }
//
//}

public class VideoChatClientSerialization(val gson: Gson) : ClientToBusGsonSerialization {
    override fun deserializeBusMessageValue(type: String, message: JsonElement): ClientToServerMessageContent {
    @Suppress("IMPLICIT_CAST_TO_ANY") val rc = when (type) {
                ConnectWith::class.simpleName -> gson.fromJson(message, ConnectWith::class.java)
                else -> throw IllegalArgumentException("$type not supported")
        }
        return rc
    }

    override fun <T> transformToServiceMessage(busMessage: BusMessageEnvelope): T{
        TODO("Need service defined in order to transform to a service command. Will need tor create" +
                "Context and Command")
        val context = Context(busMessage.connectionId,busMessage.message.messageId)
        val content = busMessage.message.content
        return when (content) {
            is ConnectWith -> com.redsquiggles.virtualvenue.videochat.ConnectWith(context,content.userIds) as T
            else -> throw IllegalArgumentException("${busMessage.message.content::class.simpleName} not supported")
        }

    }



}

public class VideoChatServiceSerialization(val gson: Gson) : ServiceToBusGsonSerialization {
    override fun serialize(message: ServerToClientMessageEnvelope): String {
        return when (message) {
            is Envelope -> gson.toJson(BusMessage(message.messageId,message.inReplyTo,message.content))
            else -> throw NotImplementedError("${message::class.simpleName} not supported")
        }
    }
}

public class APIGatewayToVideoChatMessageBus(gson: Gson,
                                             apiGatewayBridge: WebSocketGatewayBridge,
                                             clientSerializationUtilities: ClientToBusGsonSerialization,
                                             serviceSerializationUtilities: ServiceToBusGsonSerialization) :
    ApiServiceMessageBusImpl(gson,apiGatewayBridge,clientSerializationUtilities,serviceSerializationUtilities ) {
    override fun processInbound(command: ClientToServerMessageEnvelope): com.redsquiggles.communications.bus.Result<Response> {
        TODO("Not yet implemented")
    }

    override fun <T> transformToServiceMessage(busMessage: BusMessageEnvelope): T {
        return clientSerializationUtilities.transformToServiceMessage(busMessage)
    }


}
