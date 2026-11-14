package com.vetplatform

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.http.javanet.NetHttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleIdentityConfiguration {
    const val CLIENT_ID_ENV = "GOOGLE_CLIENT_ID"

    fun clientIdOrNull(): String? = System.getenv(CLIENT_ID_ENV)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

/** Verifies Google ID tokens independently of browser-side validation. */
class GoogleIdTokenIdentityVerifier(
    private val clientIdProvider: () -> String? = GoogleIdentityConfiguration::clientIdOrNull
) : GoogleIdentityVerifier {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NetHttpTransport.Builder().build()
    }
    private val googleVerifier by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val clientId = clientIdProvider() ?: throw GoogleConfigurationMissingException()
        GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
            .setAudience(listOf(clientId))
            .build()
    }

    override suspend fun verify(credential: String): VerifiedGoogleIdentity {
        if (credential.isBlank() || credential.length > MAX_CREDENTIAL_LENGTH) {
            throw InvalidGoogleCredentialException()
        }
        val clientId = clientIdProvider() ?: throw GoogleConfigurationMissingException()

        return try {
            withContext(Dispatchers.IO) {
                val parsed = try {
                    GoogleIdToken.parse(jsonFactory, credential)
                } catch (_: Exception) {
                    throw InvalidGoogleCredentialException()
                }
                if (!googleVerifier.verify(parsed)) {
                    throw InvalidGoogleCredentialException()
                }

                val payload = parsed.payload
                val subject = payload.subject?.takeIf(String::isNotBlank)
                    ?: throw InvalidGoogleCredentialException()
                if (subject.length > 255) throw InvalidGoogleCredentialException()
                val email = payload.email
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf(String::isNotBlank)
                    ?: throw InvalidGoogleCredentialException()
                if (email.length > 255 || !email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                    throw InvalidGoogleCredentialException()
                }
                if (payload.emailVerified != true) {
                    throw InvalidGoogleCredentialException()
                }

                VerifiedGoogleIdentity(
                    subject = subject,
                    email = email,
                    name = (payload["name"] as? String)
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: GoogleConfigurationMissingException) {
            throw exception
        } catch (exception: InvalidGoogleCredentialException) {
            throw exception
        } catch (exception: Exception) {
            throw GoogleVerificationUnavailableException(exception)
        }
    }

    private companion object {
        const val MAX_CREDENTIAL_LENGTH = 8192
    }
}

object GoogleAuth {
    val service by lazy {
        GoogleAuthenticationService(
            verifier = GoogleIdTokenIdentityVerifier(),
            store = GoogleIdentityRepository
        )
    }
}

class GoogleAuthenticationService(
    private val verifier: GoogleIdentityVerifier,
    private val store: GoogleIdentityStore
) {
    suspend fun signIn(credential: String): GoogleSignInResult {
        val identity = verifier.verify(credential)
        val linkedAccount = store.findByGoogleSubject(identity.subject)
        if (linkedAccount != null) {
            return linkedAccount.asSignInResult()
        }

        val emailAccount = store.findByEmail(identity.email)
        if (emailAccount != null) {
            return if (emailAccount.role == "CUSTOMER") {
                GoogleSignInResult.ExistingAccountMustLink
            } else {
                GoogleSignInResult.NotCustomer
            }
        }

        return GoogleSignInResult.Authenticated(store.createGoogleCustomer(identity))
    }

    suspend fun link(
        userId: Long,
        credential: String,
        currentPassword: String
    ): GoogleLinkResult {
        val account = store.findAccountById(userId) ?: return GoogleLinkResult.ACCOUNT_NOT_FOUND
        if (account.role != "CUSTOMER") return GoogleLinkResult.NOT_CUSTOMER
        if (account.accountStatus != "ACTIVE") return GoogleLinkResult.INACTIVE_ACCOUNT

        val identity = verifier.verify(credential)
        val linkedAccount = store.findByGoogleSubject(identity.subject)
        if (linkedAccount != null) {
            return if (linkedAccount.userId == userId) {
                GoogleLinkResult.ALREADY_LINKED
            } else {
                GoogleLinkResult.IDENTITY_IN_USE
            }
        }

        val emailAccount = store.findByEmail(identity.email)
        if (emailAccount != null && emailAccount.userId != userId) {
            return GoogleLinkResult.IDENTITY_IN_USE
        }

        val passwordHash = account.passwordHash
        val passwordMatches = currentPassword.isNotBlank() && currentPassword.length <= 72 &&
            withContext(Dispatchers.Default) {
                PasswordService.verifyPassword(currentPassword, passwordHash)
            }
        if (!passwordMatches) {
            return GoogleLinkResult.INVALID_PASSWORD
        }

        return store.linkGoogleIdentity(
            userId = userId,
            subject = identity.subject,
            expectedPasswordHash = passwordHash
        )
    }

    private fun GoogleAccount.asSignInResult(): GoogleSignInResult = when {
        role != "CUSTOMER" -> GoogleSignInResult.NotCustomer
        accountStatus != "ACTIVE" -> GoogleSignInResult.InactiveAccount
        else -> GoogleSignInResult.Authenticated(this)
    }
}
