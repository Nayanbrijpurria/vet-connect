
package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val phone: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val message: String,
    val userId: Long? = null,
    val email: String? = null,
    val role: String? = null,
    val token: String? = null
)
