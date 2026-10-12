
package com.vetplatform

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

object JwtService {

    private const val ISSUER = "vetplatform"
    private const val AUDIENCE = "vetplatform-users"

    private fun getSecret(): String {
        val secret = System.getenv("JWT_SECRET")
            ?: error("JWT_SECRET environment variable is missing")

        require(secret.toByteArray(Charsets.UTF_8).size >= 32) {
            "JWT_SECRET must contain at least 32 bytes"
        }

        return secret
    }

    fun generateToken(user: LoginUser): String {
        val now = Instant.now()
        val expiresAt = now.plus(24, ChronoUnit.HOURS)

        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(user.id.toString())
            .withClaim("userId", user.id)
            .withClaim("email", user.email)
            .withClaim("role", user.role)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .sign(Algorithm.HMAC256(getSecret()))
    }

    fun verifier(): JWTVerifier {
        return JWT.require(Algorithm.HMAC256(getSecret()))
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()
    }
}
