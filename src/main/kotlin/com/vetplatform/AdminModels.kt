
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
    val verificationStatus: String,
    val submissionNumber: Int = 1,
    val submissionSummary: String? = null
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

@Serializable
data class AdminAccountResponse(
    val userId: Long,
    val fullName: String,
    val email: String,
    val role: String,
    val accountStatus: String,
    val verificationStatus: String?
)

@Serializable
data class AdminAccountListResponse(
    val accounts: List<AdminAccountResponse>
)

@Serializable
data class AdminAccountActionRequest(
    val reason: String
)

@Serializable
data class AdminAccountActionResponse(
    val message: String,
    val account: AdminAccountResponse
)
