
package com.vetplatform

import kotlinx.serialization.Serializable

/**
 * Request sent by a veterinarian to create a consultation
 * for an existing appointment.
 */
@Serializable
data class CreateConsultationRequest(
    val appointmentId: Long,
    val chiefComplaint: String? = null,
    val clinicalNotes: String? = null,
    val diagnosis: String? = null,
    val treatmentNotes: String? = null,
    val followUpNotes: String? = null
)

/**
 * Consultation details returned by the API.
 */
@Serializable
data class ConsultationResponse(
    val id: Long,
    val appointmentId: Long,
    val petId: Long,
    val vetProfileId: Long,
    val chiefComplaint: String? = null,
    val clinicalNotes: String? = null,
    val diagnosis: String? = null,
    val treatmentNotes: String? = null,
    val followUpNotes: String? = null,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Response wrapper for a list of consultations.
 */
@Serializable
data class ConsultationListResponse(
    val consultations: List<ConsultationResponse>
)
