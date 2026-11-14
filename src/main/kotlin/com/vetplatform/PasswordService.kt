
package com.vetplatform

import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom
import java.util.Base64

object PasswordService {
    private val secureRandom = SecureRandom()

    fun hashPassword(password: String): String {
        require(password.length >= 8) {
            "Password must contain at least 8 characters"
        }

        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    /** Creates a BCrypt hash for an undisclosed, high-entropy password. */
    fun hashUnusablePassword(): String {
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        val undisclosedPassword = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes)
        return BCrypt.hashpw(undisclosedPassword, BCrypt.gensalt(12))
    }

    fun verifyPassword(
        password: String,
        passwordHash: String
    ): Boolean {
        return BCrypt.checkpw(password, passwordHash)
    }
}
