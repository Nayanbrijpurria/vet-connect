
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CreatedUser(
    val id: Long,
    val fullName: String,
    val email: String,
    val role: String
)

data class LoginUser(
    val id: Long,
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val role: String
)

object UserRepository {

    suspend fun createCustomer(
        fullName: String,
        email: String,
        passwordHash: String,
        phone: String?
    ): CreatedUser = withContext(Dispatchers.IO) {

        val sql = """
            INSERT INTO users (
                full_name,
                email,
                password_hash,
                phone,
                role
            )
            VALUES (?, ?, ?, ?, 'CUSTOMER')
            RETURNING id, full_name, email, role
        """.trimIndent()

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, fullName)
                statement.setString(2, email)
                statement.setString(3, passwordHash)
                statement.setString(4, phone)

                statement.executeQuery().use { result ->
                    check(result.next()) {
                        "User was not returned after insertion"
                    }

                    CreatedUser(
                        id = result.getLong("id"),
                        fullName = result.getString("full_name"),
                        email = result.getString("email"),
                        role = result.getString("role")
                    )
                }
            }
        }
    }

//    What does findByEmail() do?
//    - Finds a user using their email address.
//    - Returns the user's ID, name, email, password hash, and role.
//    - Returns null if no account exists.
//    - Uses a parameterized SQL query, which helps protect against SQL injection.

    suspend fun findByEmail(email: String): LoginUser? =
        withContext(Dispatchers.IO) {

            val sql = """
                SELECT id, full_name, email, password_hash, role
                FROM users
                WHERE email = ?
            """.trimIndent()

            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, email)

                    statement.executeQuery().use { result ->
                        if (result.next()) {
                            LoginUser(
                                id = result.getLong("id"),
                                fullName = result.getString("full_name"),
                                email = result.getString("email"),
                                passwordHash = result.getString("password_hash"),
                                role = result.getString("role")
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        }


    suspend fun findRoleById(userId: Long): String? =
        withContext(Dispatchers.IO) {
            val sql = """
                SELECT role
                FROM users
                WHERE id = ?
            """.trimIndent()

            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, userId)

                    statement.executeQuery().use { result ->
                        if (result.next()) {
                            result.getString("role")
                        } else {
                            null
                        }
                    }
                }
            }
        }


    suspend fun createAdmin(
        fullName: String,
        email: String,
        passwordHash: String
    ): CreatedUser = withContext(Dispatchers.IO) {
        val sql = """
        INSERT INTO users (
            full_name,
            email,
            password_hash,
            phone,
            role
        )
        VALUES (?, ?, ?, NULL, 'ADMIN')
        RETURNING id, full_name, email, role
    """.trimIndent()

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, fullName)
                statement.setString(2, email)
                statement.setString(3, passwordHash)

                statement.executeQuery().use { result ->
                    check(result.next()) {
                        "Admin account was not created."
                    }

                    CreatedUser(
                        id = result.getLong("id"),
                        fullName = result.getString("full_name"),
                        email = result.getString("email"),
                        role = result.getString("role")
                    )
                }
            }
        }
    }


}
