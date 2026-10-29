
package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
data class PendingVetResponse(
    val userId: Long,
    val fullName: String,
    val email: String,
    val phone: String?,
    val qualification: String,
    val registrationNumber: String,
    val experienceYears: Int,
    val consultationFee: Double,
    val verificationStatus: String
)

@Serializable
data class PendingVetsListResponse(
    val applications: List<PendingVetResponse>
)

@Serializable
data class VetReviewRequest(
    val rejectionReason: String? = null
)

@Serializable
data class VetReviewResponse(
    val message: String,
    val userId: Long,
    val verificationStatus: String
)

@Serializable
data class AdminVetCredentialResponse(
    val id: Long,
    val vetUserId: Long,
    val documentType: String,
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long,
    val verificationStatus: String,
    val adminNotes: String?,
    val uploadedAt: String,
    val reviewedAt: String?
)

@Serializable
data class CredentialReviewRequest(
    val adminNotes: String? = null
)

@Serializable
data class CredentialReviewResponse(
    val message: String,
    val document: AdminVetCredentialResponse
)
