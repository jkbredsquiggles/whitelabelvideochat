package com.redsquiggles.kingevents.videochat

import com.google.gson.*
import com.redsquiggles.communications.bus.*
import com.redsquiggles.communications.bus.ClientToServerMessageContent
import com.redsquiggles.squiggleprise.logging.EventLevel
import com.redsquiggles.squiggleprise.logging.InstrumentedEventType
import com.redsquiggles.squiggleprise.logging.LogEvent
import com.redsquiggles.utilities.Result
import com.redsquiggles.virtualvenue.videochat.ServiceMessage
import com.redsquiggles.virtualvenue.videochat.VideoChatService
import com.redsquiggles.virtualvenue.videochat.VideoChatServiceImpl
import com.redsquiggles.virtualvenue.videochat.parseRSAPrivateKey
import com.redsquiggles.virtualvenue.websocketlambda.*

/**
 * Video chat lambda
 */
class App : ApiGatewayWebSocketBridgeServiceImpl() {
    lateinit  var clientSerializationUtilities: VideoChatClientSerialization
    lateinit var messageBus : ApiServiceMessageBus
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
        val messageBusBase = ApiServiceMessageBusImpl(gson,this.webSocketBridge,clientSerializationUtilities,VideoChatServiceSerialization(gson))
        var signingKey= System.getenv("SIGNING_KEY")
        var apiKey = System.getenv("JITSI_API_KEY")
        var appKey = System.getenv("JITSI_APP_KEY")
        var rsaKey = parseRSAPrivateKey(signingKey)
        val videoChatService = VideoChatServiceImpl(logger,rsaKey,apiKey,appKey,messageBus)
        messageBus = APIGatewayToVideoChatMessageBus(clientSerializationUtilities,messageBusBase,videoChatService)
    }

    override fun processAuthorized(connectionId: String, messageAsJson: JsonObject) {

        // transform message
        val message = messageHelper.deserializeBusMessage(connectionId,messageAsJson,
            clientSerializationUtilities::deserializeBusMessageValue)
        val serviceMessage = clientSerializationUtilities.transformToServiceMessage<ServiceMessage>(message)
        val transformedRequest = BusMessage(message.message.messageId,message.message.inReplyTo,serviceMessage)
        val envelope = BusMessageEnvelope(connectionId,transformedRequest)
        messageBus.processInbound(envelope).errorOrNull()?.let {
            logger.log(
                LogEvent(
                    EventLevel.Warning,
                    it,
                    InstrumentedEventType.Event,
                    "Error processing input video chat message",
                    "InboundVideoChatMessageProcessingError",
                    "ResultError"
                )
            )
        }

    }

    override fun processWebSocketConnect(connectionId: String) {
        throw NotImplementedError("Don't think this will be required.")
    }

    override fun processWebSocketDisconnect(connectionId: String) {
    // This is what would be done if wiring up the chat server - i.e. send it a message telling it that a client
        // has disconnected
        // Video chat server doesn't track client connection state just yet
        // So we currently do nothing.
    //        val context = Context(UUID.randomUUID().toString(), connectionId)
//        val command = DisconnectUser(context)
//        server.process(command)
    }

}

interface VideoChatMessage
data class User(val id : String, val name: String)
data class ConnectWith(var requestedBy: User, var otherParticipants: List<User>)  : ClientToServerMessageContent, VideoChatMessage

data class VideoChatDetails(var sessionId: String, var authenticationToken: String, var host: User)


/**
 * Serialization utility to convert between client message types and the service message bus
 */
public class VideoChatClientSerialization(val gson: Gson) : ClientToBusGsonSerialization {
    override fun deserializeBusMessageValue(type: String, message: JsonElement): ClientToServerMessageContent {
    @Suppress("IMPLICIT_CAST_TO_ANY") val rc = when (type) {
                ConnectWith::class.simpleName -> gson.fromJson(message, ConnectWith::class.java)
                else -> throw IllegalArgumentException("$type not supported")
        }
        return rc
    }

    fun User.toVideoChat() : com.redsquiggles.virtualvenue.videochat.User {
        return com.redsquiggles.virtualvenue.videochat.User(this.id,this.name)
    }

    override fun <T> transformToServiceMessage(busMessage: BusMessageEnvelope): T{
        val context = Context(busMessage.connectionId,busMessage.message.messageId)
        val content = busMessage.message.content
        return when (content) {
            is ConnectWith -> com.redsquiggles.virtualvenue.videochat.StartVideoChatWith(context,
                content.requestedBy.toVideoChat(),content.otherParticipants.map { it-> it.toVideoChat()}) as T
            else -> throw IllegalArgumentException("${busMessage.message.content::class.simpleName} not supported")
        }

    }



}

/**
 * Serialization utilities to convert between service message types and the serialization bus
 */
public class VideoChatServiceSerialization(val gson: Gson) : ServiceToBusGsonSerialization {
    override fun serialize(message: ServerToClientMessageEnvelope): String {
        return when (message) {
            is Envelope -> gson.toJson(BusMessage(message.messageId,message.inReplyTo,message.content))
            else -> throw NotImplementedError("${message::class.simpleName} not supported")
        }
    }
}

/**
 * A MessageBus for use by the VideoChatService service AND the ApiGatewayWebSocketBridgeServiceImpl
 *
 */
//public class APIGatewayToVideoChatMessageBus(gson: Gson,
//                                             apiGatewayBridge: WebSocketGatewayBridge,
//                                             clientSerializationUtilities: ClientToBusGsonSerialization,
//                                             serviceSerializationUtilities: ServiceToBusGsonSerialization) :
//    ApiServiceMessageBusImpl(gson,apiGatewayBridge,clientSerializationUtilities,serviceSerializationUtilities ) {

    public class APIGatewayToVideoChatMessageBus(val clientSerializationUtilities: ClientToBusGsonSerialization,
                                                 val base: ApiServiceMessageBusImpl,
                                                 val videoChatService: VideoChatService
) : WebSocketServiceOutboundMessageBus by base, WebSocketServiceCommandBus by base, ApiServiceMessageBus {
    override fun process(command: Message): com.redsquiggles.communications.bus.Result<Response> {
        var transformedCommand  = when (command) {
            is ServerToClientMessageEnvelope -> return base.processOutbound(command)
            is BusCommand -> return base.processBusCommand(command)
            is ClientToServerMessageEnvelope -> return processInbound(command)
            else -> throw NotImplementedError("message type not supported ${command::class.simpleName}")
        }
    }


    override fun processInbound(command: ClientToServerMessageEnvelope): com.redsquiggles.communications.bus.Result<Response> {

        when (command) {
            is BusMessageEnvelope ->
            {
                when (val serviceCommand = command.message.content) {
                   is com.redsquiggles.virtualvenue.videochat.StartVideoChatWith-> videoChatService.process(serviceCommand)
                    else -> throw NotImplementedError("ServiceCommand type not supported ${serviceCommand::class.simpleName}")
               }
            }
            else -> throw NotImplementedError("Command type not supported ${command::class.simpleName}")

        }
        return com.redsquiggles.communications.bus.Result.success(Response.CommandSent)
    }

    override fun <T> transformToServiceMessage(busMessage: BusMessageEnvelope): T {
        return clientSerializationUtilities.transformToServiceMessage(busMessage)
    }


}
