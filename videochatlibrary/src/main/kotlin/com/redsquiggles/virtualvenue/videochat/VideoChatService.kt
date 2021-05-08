package com.redsquiggles.virtualvenue.videochat

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.redsquiggles.communications.bus.MessageBus
import java.security.KeyFactory
import java.security.interfaces.RSAKey
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*

interface VideoChatService {
    fun process(command: ConnectWith)
}

class VideoChatServiceImpl(val signingKey : RSAPrivateKey, val apiKey: String, val appKey: String, messageBus: MessageBus) : VideoChatService {

    //val jitsiAppKey = "vpaas-magic-cookie-de42ec9026634636b0bc0f0d30fef64e"
    //val jitsiAPIKey = "vpaas-magic-cookie-de42ec9026634636b0bc0f0d30fef64e/eb4fdc"


    // The UI will use the APPID + room name in the URL
   // https://8x8.vc/vpaas-magic-cookie-de42ec9026634636b0bc0f0d30fef64e/SampleAppPointedPrioritiesMentionQuickly

    fun createToken(user: User, roomName: String) : String {
        return JitsiJWTClaimBuilder.default().apply {
            apiKey = this@VideoChatServiceImpl.apiKey
            jitsiAppKey = appKey
            userId = user.id
            userName = user.userName
            this.roomName = roomName
        }
            .build()
            .signJitsi(signingKey)
    }

    override fun process(command: ConnectWith) {
//        val roomName = "DM" + UUID.randomUUID().toString()
//        val userClaims = mutableMapOf<String,String>()
//        val featureClaims = mutableMapOf<String,String>()
//        userClaims["moderator"] = false.toString()
//        userClaims["name"] = command.requestedBy.userName
//        userClaims["id"] = command.requestedBy.id
//        featureClaims["livestreaming"] = false.toString()
//        featureClaims["outbound-call"] = false.toString()
//        featureClaims["transcription"] = false.toString()
//        featureClaims["recording"] = false.toString()
//        val context = mutableMapOf<String,Any>()
//        context["user"] = userClaims
//        context["features"] = featureClaims
//
//        val token = JWT.create()
//            .withKeyId(jitsiAPIKey)
//            .withExpiresAt(Date.from(Instant.ofEpochSecond(Instant.now().epochSecond + 7200)))
//            .withNotBefore(Date.from(Instant.ofEpochSecond(Instant.now().epochSecond - 60)))
//            .withClaim("sub", jitsiAppKey)
//            .withClaim("room", roomName)
//            .withClaim("iss","chat")
//            .withClaim("aud","jitsi")
//            .withClaim("context",context)
//            .sign(Algorithm.RSA256(null,getKey()))
//
//        println(token)
//
//        val decoded = JWT.decode(token)
//        println(decoded.token)


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