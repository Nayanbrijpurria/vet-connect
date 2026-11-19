package com.vetplatform

import io.livekit.server.AccessToken
import io.livekit.server.CanPublish
import io.livekit.server.CanPublishData
import io.livekit.server.CanPublishSources
import io.livekit.server.CanSubscribe
import io.livekit.server.CanUpdateOwnMetadata
import io.livekit.server.RoomJoin
import io.livekit.server.RoomName
import io.livekit.server.RoomServiceClient
import io.livekit.server.WebhookReceiver
import livekit.LivekitWebhook
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LiveKitToken(
    val token: String,
    val expiresInSeconds: Long
)

interface VideoProvider {
    fun createParticipantToken(
        roomName: String,
        participantIdentity: String,
        ttlSeconds: Long
    ): LiveKitToken

    fun verifyWebhook(body: String, authorization: String?): LivekitWebhook.WebhookEvent

    suspend fun endRoom(roomName: String)
}

object LiveKitVideoProvider : VideoProvider {
    private fun apiKey(): String =
        requireNotNull(System.getenv("LIVEKIT_API_KEY")) { "LIVEKIT_API_KEY is not configured" }

    private fun apiSecret(): String =
        requireNotNull(System.getenv("LIVEKIT_API_SECRET")) { "LIVEKIT_API_SECRET is not configured" }

    val serverUrl: String
        get() = requireNotNull(System.getenv("LIVEKIT_URL")) { "LIVEKIT_URL is not configured" }

    override fun createParticipantToken(
        roomName: String,
        participantIdentity: String,
        ttlSeconds: Long
    ): LiveKitToken {
        require(ttlSeconds in 60..900) { "Invalid LiveKit token TTL" }
        val accessToken = AccessToken(apiKey(), apiSecret()).apply {
            identity = participantIdentity
            ttl = TimeUnit.SECONDS.toMillis(ttlSeconds)
            expiration = Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds))
            addGrants(
                RoomJoin(true),
                RoomName(roomName),
                CanPublish(true),
                CanPublishSources(listOf("camera", "microphone")),
                CanPublishData(false),
                CanSubscribe(true),
                CanUpdateOwnMetadata(false)
            )
        }
        return LiveKitToken(accessToken.toJwt(), ttlSeconds)
    }

    override fun verifyWebhook(body: String, authorization: String?): LivekitWebhook.WebhookEvent =
        WebhookReceiver(apiKey(), apiSecret()).receive(body, authorization)

    override suspend fun endRoom(roomName: String) = withContext(Dispatchers.IO) {
        require(roomName.matches(Regex("vet-[0-9a-fA-F-]{36}"))) {
            "Invalid LiveKit room name"
        }
        val host = serverUrl
            .replaceFirst(Regex("^wss://"), "https://")
            .replaceFirst(Regex("^ws://"), "http://")
            .trimEnd('/') + "/"
        val client = RoomServiceClient.createClient(host, apiKey(), apiSecret())
        val response = client.deleteRoom(roomName).execute()
        check(response.isSuccessful) { "LiveKit could not end the room" }
    }
}
