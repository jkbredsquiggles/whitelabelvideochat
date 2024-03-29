package com.redsquiggles.virtualvenue.videochat

import com.redsquiggles.communications.bus.*
import com.redsquiggles.squiggleprise.logging.*
import com.redsquiggles.utilities.paginate
import com.redsquiggles.virtualvenue.videochat.dao.Dao
import com.redsquiggles.virtualvenue.videochat.dao.UserExistsException
import com.redsquiggles.virtualvenue.videochat.model.User
import com.redsquiggles.virtualvenue.videochat.model.UserPaginationKey
import com.redsquiggles.virtualvenue.websocketlambda.ApiGatewayWebSocketBridgeServiceImpl

import java.security.interfaces.RSAPrivateKey
import java.time.Instant
import java.util.*

interface VideoChatService {
    fun process(command: ConnectUser)
    fun process(command: UpdateUser)
    fun process(command: DisconnectUser)

    /**
     * Create a random chat room.
     * For host and each guest, create a token so that the user can enter the room.
     * Send the tokens to host and each guest.
     */
    fun process(command: StartVideoChatWith)

    // TODO add events and state
    // connect - register with server (or just connect, if already registered)
    // accept chat invite - send message to host (and other guests?)
    // decline chat invite - send message to host (and other guests?)
    // enter chat - can this serve for both accept and enter chat?
    // leave chat - send message to host (and other guests?)
    // disconnect - still registered, but idle
    //
    // states
    // - available
    // - initiating (either sent out invite and haven't entered, or being invited)
    // - busy - in chat - do we track which chat? Probably useful for socializing
    // - idle - disconnected (or inactive for x?)
}



class VideoChatServiceImpl(val logger: Logger, val signingKey : RSAPrivateKey, val apiKey: String, val appKey: String,
                           val messageBus: MessageBus,
                           val roomNamePrefix: String,
                           val dao : Dao
) : VideoChatService {

    fun ServiceMessage.logEventForCommand(message: String, eventKey: String, level : EventLevel = EventLevel.Info) : ServiceMessage {
        logger.log(
            LogEvent(
            level = level,
            data = listOf(this),
            type = InstrumentedEventType.Event, message = message,eventKey = eventKey,
            dataKey = this::class.simpleName
        )
        )
        return this
    }
    fun Pair<ServiceMessage, User>.logEventForCommand(message: String, eventKey: String, level : EventLevel = EventLevel.Info) : Pair<ServiceMessage,User> {
        this.first.logEventForCommand(message,eventKey,level)
        return this
    }
    fun Pair<ServiceMessage,User>.sendReply(message: Any) : Pair<ServiceMessage,User> {
        sendNotificationTo(UUID.randomUUID().toString(),this.first.context.messageId, setOf(this.second)) {
                rId,iRTo,dList -> Envelope(rId,iRTo,dList, message)
        }
        return this
    }

    fun User.toDestination() : Destination {
        return Destination(this.id,this.connectionId!!)
    }

    /**
     * For the supplied list of users, send notice to the recipients that the each of those users has disconnected.
     * And recursively deal with the fact that some of the recipients may have disconnected by sending notice of their
     * disconnect to the remaining users.
     */
    fun Set<User>.sendDisconnectNotificationTo(responseId: String,inResponseTo: String?,recipients: Set<User>) {
        if (this.isEmpty() || recipients.isEmpty()) {
            return
        }

        //val newDisconnects = mutableListOf<User>()
        //val newRecipients = recipients.toMutableList()
        val newDisconnects = this.asSequence().map{ user ->
            val responses = messageBus.process(Envelope(responseId,inResponseTo,recipients.map { it.toDestination() }, Disconnected(user.id)))
            responses.errorOrNull()?.let {
                logger.log(LogEvent(EventLevel.Warning,it,InstrumentedEventType.Event,"Notification not sent","NotificationNotSent","Error"))
                when(it) {
                    is com.redsquiggles.communications.bus.Error.CommandNotSentToSomeDestinations -> {
                        it.failedDestinationIds.map { failedDestination ->
                            disconnectUser(failedDestination.id)?.let {
                                recipients.firstOrNull { r -> r.id == failedDestination.id }
                            }
                        }
                    }
                    else -> {
                        listOf<User>()
                    }
                }
            }
        }
            .filterNotNull()
            .flatten()
            .filterNotNull()
            .toSet()
        val newRecipients = recipients.minus(newDisconnects)
        newDisconnects.sendDisconnectNotificationTo(responseId,inResponseTo,newRecipients)
    }


    fun sendNotificationTo(responseId: String, inResponseTo: String?,recipients: Set<User>,messageGenerator: (String,String?,List<Destination>)-> ServerToClientMessageEnvelope) {
        logger.log(LogEvent(EventLevel.Info,recipients,InstrumentedEventType.Event,"processing send notification to","sendNotificaitonTo","SetUsers"))

        if (recipients.isEmpty()) {
            return
        }
        val message = messageGenerator(responseId,inResponseTo,recipients.map { it.toDestination() })
        //val newDisconnects = mutableListOf<User>()
        //val newRecipients = recipients.toMutableList()
        var responses = messageBus.process(
            message
        )
        responses.errorOrNull()?.let {
            logger.log(LogEvent(EventLevel.Warning,it,InstrumentedEventType.Event,"Notification not sent","NotificationNotSent","Error"))
            val newDisconnects = when(it) {
                is com.redsquiggles.communications.bus.Error.CommandNotSentToSomeDestinations -> {
                    it.failedDestinationIds.map { failedDestination ->
                        disconnectUser(failedDestination.id)?.let {
                            recipients.firstOrNull { r -> r.id == failedDestination.id }
                        }
                    }
                }
                else -> {
                    listOf<User>()
                }

            }.filterNotNull().toSet()
            val newRecipients = recipients.minus(newDisconnects)
            newDisconnects.sendDisconnectNotificationTo(responseId,inResponseTo,newRecipients)
        }



    }


    //val jitsiAppKey = "vpaas-magic-cookie-de42ec9026634636b0bc0f0d30fef64e"
    //val jitsiAPIKey = "vpaas-magic-cookie-de42ec9026634636b0bc0f0d30fef64e/eb4fdc"


    // The UI will use the APPID + room name in the URL
   // https://8x8.vc/vpaas-magic-cookie-de42ec9026634636b0bc0f0d30fef64e/SampleAppPointedPrioritiesMentionQuickly

    fun createToken(user: User, roomName: String) : String {
        return JitsiJWTClaimBuilder.default().apply {
            apiKey = this@VideoChatServiceImpl.apiKey
            jitsiAppKey = appKey
            userId = user.id
            userName = user.name
            this.roomName = roomName
        }
            .build()
            .signJitsi(signingKey)
    }

    fun getAllUsers() : List<User> {
        val allUsers = paginate<UserPaginationKey,User>(
            { k-> dao.getUsers(k)},
            { p-> p.second}
        )
        return allUsers
    }

    fun getActiveUsers() : List<User> {
        val onlineUsers = paginate<UserPaginationKey,User>(
            { k-> dao.getUsers(k)},
            { p-> p.second.filter { it.connectionId != null }}
        )
        return onlineUsers
    }

    /**
     * Update dao to reflect the fact that a user has disconnected
     */
    fun disconnectUser(user : User) : User? {
        return dao.upsertUser(user.copy(connectionId = null))
    }

    /**
     * Update dao to reflect the fact that a user has disconnected
     */
    fun disconnectUser(userId : String) : User? {
        return dao.getUser(userId)?.let{
            disconnectUser(it)
        }
    }

    fun ServiceMessage.notifyBusOfDisconnect() : ServiceMessage {
        messageBus.process(Disconnect(this.context.connectionId!!))
        return this
    }

    /**
     * get user from DAO and update with current user information
     */
    fun getAndSyncUser(user: User) : User? {
        var currentUser = dao.getUser(user.id) ?: return null
        currentUser = currentUser.copy(connectionId = user.connectionId, lastConnected = Instant.now(), name = user.name)
        dao.upsertUser(currentUser)!!
        return currentUser
    }

    /**
     * get user from DAO and update with current connection info.
     * If that user's connectionId does not match the supplied.
     * return user and boolean that indicates if the data was already synced
     *
     */
    fun getAndSyncUser(userId: String, connectionId: String) : User? {
        var user = dao.getUser(userId) ?: return null
        user = user.copy(connectionId = connectionId, lastConnected = Instant.now())
        dao.upsertUser(user)!!
        return user
    }
    override fun process(command: ConnectUser) {
        logger.log(LogEvent(EventLevel.Info,command,InstrumentedEventType.Event,"processing connect user","ConnectUser","ConnectUser"))

        // add user to users - top level will create ConnectUser from request.
        // For a lambda, the information need not be provided by the UI, it
        // will be parsed from the jwt token in the request context

        // Add user. If already present and they match, ensure status is active
        // Notify others of newly connected user

        val connectionId = command.context.connectionId!!
        // If already present,
        var user: User? = User(command.user.id,command.user.name, Instant.now(), Instant.now(),connectionId)
        var shouldNotify = true
        try {
            user = dao.createUser(user!!)
        } catch (exception : UserExistsException) {
            user = getAndSyncUser(user!!)
        }
        // If user not found then tell the bus to end the connection
        if (user == null) {
            command.logEventForCommand("User not found","UserNotFound",EventLevel.Warning)
                .notifyBusOfDisconnect()
            return
        }

        // get active users
        val onlineUsers = getActiveUsers().toSet()

        // Disconnect those on the same connection
        onlineUsers.filter { it.connectionId == connectionId && it.id != command.user.id }.forEach{
            logger.log(LogEvent(
                level = EventLevel.Warning,
                data = listOf("commandConnectionId" to connectionId, "userId" to it.id),
                type = InstrumentedEventType.Event, message = "Disconnect user with same connection",eventKey = "AmbiguousConnection",
                dataKey = "AmbiguousConnection"
            ))
            disconnectUser(it)
            messageBus.process(Disconnect(it.connectionId!!))
        }


        // Notify all active of newly connected user - serves as confirmation to newly connected user AND announcement
        // to others
        val responseId = UUID.randomUUID().toString()
            sendNotificationTo(responseId,command.context.messageId,onlineUsers) {
                    rId,iRTo,dList -> Envelope(rId,iRTo,dList, NewConnection(VideoChatUser(user.id, user.name)))
            }

        // send list of users to newly connected user

        sendNotificationTo(responseId,command.context.messageId,setOf(user)) {
                rId,iRTo,dList -> Envelope(rId,iRTo,dList, UserList(onlineUsers.map { VideoChatUser(it.id,it.name) }))
        }

        command.logEventForCommand("Processed ConnectUser","ProcessedConnectUser",EventLevel.Info)

    }

    override fun process(command: UpdateUser) {
        logger.log(LogEvent(EventLevel.Info,command,InstrumentedEventType.Event,"processing update user","ConnectUser","UpdateUser"))


        val connectionId = command.context.connectionId!!
        var currentUser = dao.getUser(command.user.id)
        if (currentUser == null) {
            command.logEventForCommand("User not found","UserNotFound",EventLevel.Warning)
                .notifyBusOfDisconnect()
            return
        }
        if (currentUser.connectionId != connectionId) {
            command.logEventForCommand("ConnectionId mismatch","ConnectionIdMismatch",EventLevel.Warning)
                .notifyBusOfDisconnect()
            return
        }

        var areKeyFieldsTheSame =
                currentUser.name == command.user.name
        currentUser = currentUser.copy( name = command.user.name, lastConnected = Instant.now())

        if (!areKeyFieldsTheSame) {
            dao.upsertUser(currentUser)!!
            val onlineUsers = getActiveUsers().toSet()

            // Notify all active of newly updated user - serves as confirmation to newly connected user AND announcement
            // to others
            val responseId = UUID.randomUUID().toString()
            sendNotificationTo(responseId,command.context.messageId,onlineUsers) {
                    rId,iRTo,dList -> Envelope(rId,iRTo,dList, NewConnection(VideoChatUser(currentUser.id,currentUser.name)))
            }
        }

        command.logEventForCommand("Processed UpdateUser","ProcessedUpdateUser",EventLevel.Info)


    }

    override fun process(command: DisconnectUser) {
        logger.log(LogEvent(EventLevel.Info,command,InstrumentedEventType.Event,"processing disconnect user","DisconnectUser","DisconnectUser"))
        // Process disconnect
        val onlineUsers = getActiveUsers()
        val usersToDisconnect = onlineUsers.filter { command.context.connectionId == it.connectionId }
            .mapNotNull { disconnectUser(it) }
            .toSet()
        val remainingUsers = onlineUsers.toSet().minus(usersToDisconnect)
        usersToDisconnect.sendDisconnectNotificationTo(UUID.randomUUID().toString(),command.context.messageId,remainingUsers)

    }


    override fun process(command: StartVideoChatWith) {

        logger.log(LogEvent(EventLevel.Info,command,InstrumentedEventType.Event,"processing start chat","StartVideoChatWith","StartVideoChatWith"))

        // For now, the room name is hard coded
        val roomName = roomNamePrefix + "DM" + UUID.randomUUID().toString()
        val clientRoomName = this.appKey + "/" + roomName

        // Get user and invitees
        val user = getAndSyncUser(command.requestedById,command.context.connectionId!!)
        if (user == null) {
            command.logEventForCommand("User not found","UserNotFound",EventLevel.Warning)
                .notifyBusOfDisconnect()
            return
        }

        val invitees = command.otherPartyIds.map {
            val invitee = dao.getUser(it)
            it to invitee
        }.filter{ it.second?.connectionId != null}

        if (invitees.count{it-> it.second != null} == 0) {
            (command to user).logEventForCommand(
                "Invalid conversation invite request. Invitees not found.",
                "InvalidInviteToConversationRequest", EventLevel.Warning
            )
                .sendReply(ErrorMessage(Error.BadRequest))
            return
        }

        // for all connected participants (both the user that made the request and the invited participants)
        // create and send a message with the details of the video chat
        val messageId = UUID.randomUUID().toString()
        invitees
            .mapNotNull { it.second }
            .plus(user)
            .forEach {
                var token = createToken(it, roomName)
                logger.log(LogEvent(EventLevel.Info,
                    mapOf("userId" to user.id,"token" to token,"roomName" to roomName),
                    InstrumentedEventType.Event,"Sending token","SendingToken","mapOfTokenStuff"
                ))
                sendNotificationTo(messageId,command.context.messageId, setOf(it)) {
                        rId,iRTo,dList ->
                           Envelope(rId, iRTo, dList, VideoChatDetails(clientRoomName, token, user.id))
                }
            }
    }


    // Will need to work out decline logic - given that the session may host many participants,
    // that may take a while to join. It may be that the decline will only mean anything as
    // a way of notifying the host that ALL other particpants have declined.


}