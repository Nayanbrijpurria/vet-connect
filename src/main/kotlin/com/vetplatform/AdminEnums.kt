package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
enum class AccountRole { CUSTOMER, VET, ADMIN }

@Serializable
enum class AccountStatus { ACTIVE, DISABLED }

@Serializable
enum class VetVerificationStatus { PENDING, APPROVED, REJECTED, SUSPENDED }

@Serializable
enum class CredentialDocumentType { DEGREE_CERTIFICATE, REGISTRATION_CERTIFICATE, ADDITIONAL_CERTIFICATE }

@Serializable
enum class AppointmentStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }
