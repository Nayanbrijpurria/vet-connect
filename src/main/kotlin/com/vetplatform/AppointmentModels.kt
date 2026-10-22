
package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
data class CreateAppointmentRequest(
    val petId: Long,
    val vetUserId: Long,
    val scheduledStart: String,
    val scheduledEnd: String
)

@Serializable
data class AppointmentResponse(
    val id: Long,
    val customerId: Long,
    val petId: Long,
    val vetUserId: Long,
    val scheduledStart: String,
    val scheduledEnd: String,
    val status: String,
    val consultationFee: Double,
    val meetingUrl: String? = null
)

@Serializable
data class AppointmentListResponse(
    val appointments: List<AppointmentResponse>
)

@Serializable
data class AppointmentActionRequest(
    val action: String
)


@Serializable
data class AvailableAppointmentSlot(
    val start: String,
    val end: String
)

@Serializable
data class AvailableAppointmentSlotsResponse(
    val vetUserId: Long,
    val date: String,
    val timezone: String,
    val slots: List<AvailableAppointmentSlot>
)
