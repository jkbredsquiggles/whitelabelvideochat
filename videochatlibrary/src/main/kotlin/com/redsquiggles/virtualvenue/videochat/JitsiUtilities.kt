package com.redsquiggles.virtualvenue.videochat

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.*


fun parseRSAPrivateKey(key : String) : RSAPrivateKey {

    var encoded = Base64.getDecoder().decode(key)
    val kf = KeyFactory.getInstance("RSA")
    val keySpec = PKCS8EncodedKeySpec(encoded)
    return kf.generatePrivate(keySpec) as RSAPrivateKey

}
fun parseRSAPublicKey(key : String) : RSAPublicKey {

    var encoded = Base64.getDecoder().decode(key)
    val kf = KeyFactory.getInstance("RSA")
    val keySpec = X509EncodedKeySpec(encoded)
    return kf.generatePublic(keySpec) as RSAPublicKey

}

fun (JWTCreator.Builder).signJitsi(privateKey: RSAPrivateKey) : String {
    return this.sign(Algorithm.RSA256(null,privateKey))
}

class JitsiJWTClaimBuilder {
    lateinit var roomName: String
    var userClaims = mutableMapOf<String,String>()
    var featureClaims = mutableMapOf<String,String>()
    lateinit var jitsiAppKey: String
    lateinit var apiKey: String
    lateinit var expiresAt : Date
    lateinit var notBefore : Date
    lateinit var userId: String
    lateinit var userName: String

    companion object {
        val issuer = "chat"
        val aud = "jitsi"


        private fun JitsiJWTClaimBuilder.defaultUserClaims() = apply {
            userClaims["moderator"] = false.toString()
        }
        private fun JitsiJWTClaimBuilder.defaultFeatureClaims() = apply {
            featureClaims["livestreaming"] = false.toString()
            featureClaims["outbound-call"] = false.toString()
            featureClaims["transcription"] = false.toString()
            featureClaims["recording"] = false.toString()
        }

        fun default() : JitsiJWTClaimBuilder {
            return JitsiJWTClaimBuilder()
                .defaultUserClaims()
                .defaultFeatureClaims()
                .apply{
                    expiresAt = Date.from(Instant.ofEpochSecond(Instant.now().epochSecond + 7200))
                    notBefore =Date.from(Instant.ofEpochSecond(Instant.now().epochSecond -60))
                }
        }

    }

    fun build() : JWTCreator.Builder {
        val context = mutableMapOf<String,Any>()
        userClaims["name"] = userName
        userClaims["id"] = userId
        context["user"] = userClaims
        context["features"] = featureClaims
        return JWT.create()
            .withKeyId(this.apiKey)
            .withExpiresAt(this.expiresAt)
            .withNotBefore(this.notBefore)
            .withClaim("sub",this.jitsiAppKey)
            .withClaim("room",this.roomName)
            .withClaim("iss", issuer)
            .withClaim("aud", aud)
            .withClaim("context", context)

    }

}
