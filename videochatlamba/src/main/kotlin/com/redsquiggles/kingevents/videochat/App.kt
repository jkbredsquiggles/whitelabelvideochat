package com.redsquiggles.kingevents.videochat

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse
import com.google.gson.*
import com.redsquiggles.communications.bus.Error
import com.redsquiggles.communications.bus.Message
import com.redsquiggles.communications.bus.MessageBus
import com.redsquiggles.communications.bus.Response
import com.redsquiggles.utilities.Result
import com.redsquiggles.virtualvenue.websocketlambda.APIGatewayBridge
import com.redsquiggles.virtualvenue.websocketlambda.ApiGatewayWebSocketBridgeServiceImpl
import com.redsquiggles.virtualvenue.websocketlambda.EventAndContext

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
            this.websocketBridge,
            clientSerializationUtilities,
        VideoChatServiceSerialization(gson))
        messageHelper
    }

    override fun processAuthorized(connectionId: String, messageAsJson: JsonObject): APIGatewayV2WebSocketResponse {
        TODO("Not yet implemented")

        // transform message
        val message = messageHelper.deserializeBusMessage(connectionId,messageAsJson,
            clientSerializationUtilities::deserializeBusMessageValue)
        clientSerializationUtilities.transformToServiceMessage<VideoChatCommand>(message)

    }

    override fun processWebSocketDisconnect(connectionId: String) {
        TODO("Not yet implemented")
    }

}

// outbound messages - server to client

// These need to be defined in the video chat library or services library
/**
 * Destination has both the chat User Id and the connectionId to simplify and improve efficiency for dealing
 * with fallout from failures sending messages - e.g. to disconnect the message recipient
 */
data class Destination( var id: String, var connectionId : String)

interface Destinations {
    var  destinations : List<Destination>
}

// Note sure which of these generic content and envelop interfaces are required.
interface ServerToClientMessageEnvelope : Message {
    var  destinations : List<Destination>
}
interface ServerToClientMessageContent : Message

// inbound messages client to server

interface ClientToServerMessageEnvelope : Message
interface ClientToServerMessageContent : Message

interface BusCommand : Message


/**
 * An envelope for message sent from server to client(S)
 */
// Not sure why Envelope should be a Destinations. commented out as an experiment
//data class Envelope(var messageId: String, var inReplyTo: String?, override var destinations: List<Destination>, var content: Any) : Destinations, ServerToClientMessageContent
data class Envelope(var messageId: String, var inReplyTo: String?, override var destinations: List<Destination>, var content: Any) : ServerToClientMessageEnvelope

/**
 * A bus command to disconnect a client
 */
data class Disconnect(var connectionId: String) : BusCommand

/**
 * An envelope for a single message sent from client to server
 */
data class  BusMessageEnvelope(var connectionId : String, var message: BusMessage) :ClientToServerMessageEnvelope

/**
 * An a single message sent between client and server
 */
data class  BusMessage(var messageId : String, var inReplyTo: String?, var type: String, var content: Any) : ClientToServerMessageContent {
    constructor(messageId: String, inResponseTo: String?, value: Any) : this(messageId,inResponseTo,value::class.simpleName!!,value)
}




// end of section that should be moved to vidochatlibrary


interface VideoChatMessage
data class ConnectWith(var userIds: List<String>)  : ClientToServerMessageContent, VideoChatMessage

// Application/Service Specific GSon (de)serialization methods
interface ClientToBusGsonSerialization {
    // General
    fun deserializeBusMessageValue(type: String,message: JsonElement) : ClientToServerMessageContent
//    @Suppress("IMPLICIT_CAST_TO_ANY") val rc = when (type) {
//        Connect::class.simpleName -> gson.fromJson(value, Connect::class.java)
//        SendMessage::class.simpleName -> gson.fromJson(value,SendMessage::class.java)
//        Disconnect::class.simpleName -> gson.fromJson(value, Disconnect::class.java)
//        com.redsquiggles.virtualvenue.ktorchat.CreateConversation::class.simpleName ->
//            gson.fromJson(value, com.redsquiggles.virtualvenue.ktorchat.CreateConversation::class.java)
//        else -> throw IllegalArgumentException("$type not supported")
//    }

    // General
    fun <T> transformToServiceMessage(busMessage: BusMessageEnvelope) : T
}

// Application/Service Specific GSon (de)serialization methods for messages between the service and the bus
interface ServiceToBusGsonSerialization {
    // outbound API specific
    fun serialize(message: ServerToClientMessageEnvelope): String
}

interface WebSocketServiceMessageBus {
    fun processInbound(command: ClientToServerMessageEnvelope) : com.redsquiggles.communications.bus.Result<Response>

    fun processOutbound(command: ServerToClientMessageEnvelope) : com.redsquiggles.communications.bus.Result<Response>

    fun processBusCommand(command: BusCommand) : com.redsquiggles.communications.bus.Result<Response>

    fun <T> transformToServiceMessage(busMessage: BusMessageEnvelope) : T
}

interface APIGatewayMessageUtilities {
    fun deserializeBusMessage(connectionId: String, parsed: JsonObject, contentTransformer: (String, JsonElement) -> ClientToServerMessageContent) : BusMessageEnvelope
}

class APIGatewayMessageUtilitiesImpl(val gson: Gson) : APIGatewayMessageUtilities {
    // API Gateway specific parsing function that will be common to most API applications
    override fun deserializeBusMessage(connectionId: String, parsed: JsonObject, contentTransformer: (String, JsonElement) -> ClientToServerMessageContent) : BusMessageEnvelope {
        val type = gson.fromJson(parsed.get("type"), String::class.java)
        val messageId = gson.fromJson(parsed.get("messageId"), String::class.java)
        val inReplyTo = parsed.get("inReplyTo")?.let {
            gson.fromJson(it, String::class.java)
        }
        //val message = gson.fromJson(this, BusMessage::class.javaObjectType)
        val value = parsed.get("value")
        val messageContent = contentTransformer(type,value)
        val busMessage = BusMessage(messageId,inReplyTo,type,messageContent)

        return BusMessageEnvelope(connectionId,busMessage)
    }
}

// Each application implements its own implementation for transforming BusMessageEnevelop content to
// the messages required by the service
// Each application implements its own method for processing inbound methods - i.e. calling the appropriate service
abstract class ApiServiceMessageBus(val gson: Gson, val apiGatewayBridge: APIGatewayBridge,
                                    val clientSerializationUtilities : ClientToBusGsonSerialization,
                                    val serviceSerializationUtilities: ServiceToBusGsonSerialization
) : MessageBus,WebSocketServiceMessageBus {


    // API Gateway specific parsing function that will be common to most API applications
    fun deserializeBusMessage(connectionId: String, parsed: JsonObject) : BusMessageEnvelope {
        val type = gson.fromJson(parsed.get("type"), String::class.java)
        val messageId = gson.fromJson(parsed.get("messageId"), String::class.java)
        val inReplyTo = parsed.get("inReplyTo")?.let {
            gson.fromJson(it, String::class.java)
        }
        //val message = gson.fromJson(this, BusMessage::class.javaObjectType)
        val value = parsed.get("value")
        val messageContent = clientSerializationUtilities.deserializeBusMessageValue(type,value)
        val busMessage = BusMessage(messageId,inReplyTo,type,messageContent)

        return BusMessageEnvelope(connectionId,busMessage)
    }

    // General
    //abstract fun <T> transformToServiceMessage(busMessage: BusMessageEnvelope) : T

    // This would be implemented by the logic in processFrame in the Ktor ChatApplication, except connect and disconnect handling
    // would be handled by the bus comment
    // This would be implemented by the logic in handleWebSocketMessage in the API Gateway Apps.
    //abstract fun processInbound(command: ClientToServerMessageEnvelope) : com.redsquiggles.communications.bus.Result<Response>

    override fun processOutbound(command: ServerToClientMessageEnvelope) : com.redsquiggles.communications.bus.Result<Response> {
        val serializedMessage = serviceSerializationUtilities.serialize(command)
        val errors  = command.destinations.mapNotNull {
            apiGatewayBridge.sendMessageToClient(it.connectionId, serializedMessage).let {result ->
                if (result.isFailure) {
                    null
                } else {
                    it
                }
            }
        }
        return if (errors.count() > 0) {
            com.redsquiggles.communications.bus.Result.failure(Error.CommandNotSentToSomeDestinations(errors))
        } else {
            com.redsquiggles.communications.bus.Result.success(Response.CommandSent)
        }

    }

    override fun processBusCommand(command: BusCommand) : com.redsquiggles.communications.bus.Result<Response> {
        when (command) {
            is Disconnect -> {
                apiGatewayBridge.disconnectClient(command.connectionId)
                return com.redsquiggles.communications.bus.Result.success(Response.CommandSent)
            }
            else -> throw NotImplementedError("message type not supported ${command::class.simpleName}")
        }
    }


    override fun process(command: Message): com.redsquiggles.communications.bus.Result<Response> {
        var transformedCommand  = when (command) {
            is ServerToClientMessageEnvelope -> return processOutbound(command)
            is BusCommand -> return processBusCommand(command)
            is ClientToServerMessageEnvelope -> return processInbound(command)
            else -> throw NotImplementedError("message type not supported ${command::class.simpleName}")
        }

    }

}

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
        return when (busMessage.message.content) {
            is ConnectWith -> busMessage.message.content as T
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
                                             apiGatewayBridge: APIGatewayBridge,
                                             clientSerializationUtilities: ClientToBusGsonSerialization,
                                             serviceSerializationUtilities: ServiceToBusGsonSerialization) :
    ApiServiceMessageBus(gson,apiGatewayBridge,clientSerializationUtilities,serviceSerializationUtilities ) {
    override fun processInbound(command: ClientToServerMessageEnvelope): com.redsquiggles.communications.bus.Result<Response> {
        TODO("Not yet implemented")
    }

    override fun <T> transformToServiceMessage(busMessage: BusMessageEnvelope): T {
        return clientSerializationUtilities.transformToServiceMessage(busMessage)
    }


}
