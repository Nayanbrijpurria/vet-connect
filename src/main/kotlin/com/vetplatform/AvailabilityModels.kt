
package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
data class CreateAvailabilityRequest(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val slotDurationMinutes: Int = 30
)

@Serializable
data class AvailabilityResponse(
    val id: Long,
    val vetUserId: Long,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val slotDurationMinutes: Int,
    val active: Boolean
)

@Serializable
data class AvailabilityListResponse(
    val availability: List<AvailabilityResponse>
)

@Serializable
data class CreateTimeOffRequest(
    val startsAt: String,
    val endsAt: String,
    val reason: String? = null
)

@Serializable
data class TimeOffResponse(
    val id: Long,
    val vetUserId: Long,
    val startsAt: String,
    val endsAt: String,
    val reason: String? = null
)

@Serializable
data class TimeOffListResponse(
    val timeOff: List<TimeOffResponse>
)
