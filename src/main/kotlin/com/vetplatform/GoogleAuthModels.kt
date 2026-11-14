package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
data class GoogleSignInRequest(
    val credential: String
)

@Serializable
data class GoogleLinkRequest(
    val credential: String,
    val currentPassword: String
)

@Serializable
data class GoogleClientConfigResponse(
    val clientId: String
)

data class VerifiedGoogleIdentity(
    val subject: String,
    val email: String,
    val name: String?
)

data class GoogleAccount(
    val userId: Long,
    val fullName: String,
    val email: String,
    val role: String,
    val accountStatus: String,
    val passwordHash: String
)

fun interface GoogleIdentityVerifier {
    suspend fun verify(credential: String): VerifiedGoogleIdentity
}

interface GoogleIdentityStore {
    suspend fun findByGoogleSubject(subject: String): GoogleAccount?
    suspend fun findByEmail(email: String): GoogleAccount?
    suspend fun createGoogleCustomer(identity: VerifiedGoogleIdentity): GoogleAccount
    suspend fun findAccountById(userId: Long): GoogleAccount?
    suspend fun linkGoogleIdentity(
        userId: Long,
        subject: String,
        expectedPasswordHash: String
    ): GoogleLinkResult
}

sealed interface GoogleSignInResult {
    data class Authenticated(val account: GoogleAccount) : GoogleSignInResult
    data object ExistingAccountMustLink : GoogleSignInResult
    data object NotCustomer : GoogleSignInResult
    data object InactiveAccount : GoogleSignInResult
}

enum class GoogleLinkResult {
    LINKED,
    ALREADY_LINKED,
    IDENTITY_IN_USE,
    INVALID_PASSWORD,
    ACCOUNT_NOT_FOUND,
    NOT_CUSTOMER,
    INACTIVE_ACCOUNT
}

class GoogleConfigurationMissingException : RuntimeException()
class InvalidGoogleCredentialException : RuntimeException()
class GoogleVerificationUnavailableException(cause: Throwable) : RuntimeException(cause)
class GoogleIdentityConflictException : RuntimeException()

