
package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
data class VetRegistrationRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val phone: String? = null,
    val qualification: String,
    val registrationNumber: String,
    val experienceYears: Int,
    val consultationFee: Double
)

@Serializable
data class VetRegistrationResponse(
    val message: String,
    val userId: Long? = null,
    val email: String? = null,
    val role: String? = null,
    val verificationStatus: String? = null
)

@Serializable
data class VetVerificationStatusResponse(
    val userId: Long,
    val registrationNumber: String,
    val verificationStatus: String
)


@kotlinx.serialization.Serializable
data class ApprovedVetResponse(
    val userId: Long,
    val fullName: String,
    val qualification: String,
    val experienceYears: Int,
    val consultationFee: Double
)

