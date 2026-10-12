
package com.vetplatform

import org.mindrot.jbcrypt.BCrypt

object PasswordService {

    fun hashPassword(password: String): String {
        require(password.length >= 8) {
            "Password must contain at least 8 characters"
        }

        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    fun verifyPassword(
        password: String,
        passwordHash: String
    ): Boolean {
        return BCrypt.checkpw(password, passwordHash)
    }
}
