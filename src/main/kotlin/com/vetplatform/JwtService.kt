
package com.vetplatform

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 900
)

object JwtService {

    private const val ISSUER = "vetplatform"
    private const val AUDIENCE = "vetplatform-users"

    // Access token validity: 15 minutes
    const val ACCESS_TOKEN_EXPIRY_SECONDS = 900L

    // Refresh token validity: 7 days
    const val REFRESH_TOKEN_EXPIRY_SECONDS = 604800L

    private val secureRandom = SecureRandom()

    private fun getSecret(): String {
        val secret = System.getenv("JWT_SECRET")
            ?: error("JWT_SECRET environment variable is missing")

        require(secret.toByteArray(Charsets.UTF_8).size >= 32) {
            "JWT_SECRET must contain at least 32 bytes"
        }

        return secret
    }

    /**
     * Generates a short-lived JWT access token.
     */
    fun generateAccessToken(user: LoginUser): String {
        return generateAccessToken(user.id, user.email, user.role)
    }

    fun generateAccessToken(user: RefreshSessionUser): String {
        return generateAccessToken(user.userId, user.email, user.role)
    }

    private fun generateAccessToken(userId: Long, email: String, role: String): String {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(ACCESS_TOKEN_EXPIRY_SECONDS)

        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId.toString())
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("role", role)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .sign(Algorithm.HMAC256(getSecret()))
    }

    /**
     * Generates a cryptographically secure opaque refresh token.
     */
    fun generateRefreshToken(): String {
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes)
    }

    /**
     * Hashes a refresh token before storing it in the database.
     * Never store the original refresh token in PostgreSQL.
     */
    fun hashRefreshToken(refreshToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(refreshToken.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }

    /**
     * Generates both access and refresh tokens.
     */
    fun generateTokenPair(user: LoginUser): TokenPair {
        return TokenPair(
            accessToken = generateAccessToken(user),
            refreshToken = generateRefreshToken()
        )
    }

    /**
     * Verifies JWT access tokens.
     */
    fun verifier(): JWTVerifier {
        return JWT.require(Algorithm.HMAC256(getSecret()))
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()
    }
}
