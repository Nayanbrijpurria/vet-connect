package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
enum class VideoSessionStatus { SCHEDULED, ACTIVE, ENDED, EXPIRED }

@Serializable
data class VideoSessionResponse(
    val appointmentId: Long,
    val status: VideoSessionStatus,
    val scheduledStart: String,
    val scheduledEnd: String,
    val joinAvailable: Boolean
)

@Serializable
data class VideoJoinResponse(
    val appointmentId: Long,
    val roomName: String,
    val serverUrl: String,
    val participantToken: String,
    val participantIdentity: String,
    val expiresIn: Long
)
