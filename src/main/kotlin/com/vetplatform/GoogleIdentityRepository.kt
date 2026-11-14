package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

object GoogleIdentityRepository : GoogleIdentityStore {
    private const val PROVIDER = "GOOGLE"

    override suspend fun findByGoogleSubject(subject: String): GoogleAccount? =
        withContext(Dispatchers.IO) {
            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(
                    """SELECT u.id, u.full_name, u.email, u.role, u.account_status, u.password_hash
                       FROM auth_identities i
                       JOIN users u ON u.id = i.user_id
                       WHERE i.provider = ? AND i.provider_subject = ?""".trimIndent()
                ).use { statement ->
                    statement.setString(1, PROVIDER)
                    statement.setString(2, subject)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.toGoogleAccount() else null
                    }
                }
            }
        }

    override suspend fun findByEmail(email: String): GoogleAccount? =
        withContext(Dispatchers.IO) {
            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(
                    """SELECT id, full_name, email, role, account_status, password_hash
                       FROM users
                       WHERE LOWER(email) = LOWER(?)
                       ORDER BY id
                       LIMIT 1""".trimIndent()
                ).use { statement ->
                    statement.setString(1, email)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.toGoogleAccount() else null
                    }
                }
            }
        }

    override suspend fun createGoogleCustomer(identity: VerifiedGoogleIdentity): GoogleAccount =
        withContext(Dispatchers.IO) {
            val unusablePasswordHash = withContext(Dispatchers.Default) {
                PasswordService.hashUnusablePassword()
            }
            DatabaseFactory.withConnection { connection ->
                transaction(connection) {
                    // Serialize new Google accounts for the normalized email so
                    // differently-cased addresses cannot race into duplicates.
                    connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock(hashtext(LOWER(?)))"
                    ).use { statement ->
                        statement.setString(1, identity.email)
                        statement.executeQuery().use { it.next() }
                    }
                    val emailExists = connection.prepareStatement(
                        "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?) LIMIT 1"
                    ).use { statement ->
                        statement.setString(1, identity.email)
                        statement.executeQuery().use { it.next() }
                    }
                    if (emailExists) throw GoogleIdentityConflictException()

                    val account = connection.prepareStatement(
                        """INSERT INTO users (full_name, email, password_hash, phone, role)
                           VALUES (?, ?, ?, NULL, 'CUSTOMER')
                           RETURNING id, full_name, email, role, account_status, password_hash""".trimIndent()
                    ).use { statement ->
                        val name = identity.name?.take(150)?.takeIf(String::isNotBlank)
                            ?: identity.email.substringBefore('@').take(150).ifBlank { "Google Customer" }
                        statement.setString(1, name)
                        statement.setString(2, identity.email)
                        statement.setString(3, unusablePasswordHash)
                        statement.executeQuery().use { result ->
                            check(result.next()) { "Google customer account was not returned" }
                            result.toGoogleAccount()
                        }
                    }

                    connection.prepareStatement(
                        """INSERT INTO auth_identities (user_id, provider, provider_subject)
                           VALUES (?, ?, ?)""".trimIndent()
                    ).use { statement ->
                        statement.setLong(1, account.userId)
                        statement.setString(2, PROVIDER)
                        statement.setString(3, identity.subject)
                        statement.executeUpdate()
                    }
                    account
                }
            }
        }

    override suspend fun findAccountById(userId: Long): GoogleAccount? =
        withContext(Dispatchers.IO) {
            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(
                    """SELECT id, full_name, email, role, account_status, password_hash
                       FROM users WHERE id = ?""".trimIndent()
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.toGoogleAccount() else null
                    }
                }
            }
        }

    override suspend fun linkGoogleIdentity(
        userId: Long,
        subject: String,
        expectedPasswordHash: String
    ): GoogleLinkResult = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            transaction(connection) {
                val accountState = connection.prepareStatement(
                    """SELECT role, account_status, password_hash
                       FROM users WHERE id = ? FOR UPDATE""".trimIndent()
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.executeQuery().use { result ->
                        if (!result.next()) null else Triple(
                            result.getString("role"),
                            result.getString("account_status"),
                            result.getString("password_hash")
                        )
                    }
                } ?: return@transaction GoogleLinkResult.ACCOUNT_NOT_FOUND

                if (accountState.first != "CUSTOMER") return@transaction GoogleLinkResult.NOT_CUSTOMER
                if (accountState.second != "ACTIVE") return@transaction GoogleLinkResult.INACTIVE_ACCOUNT
                if (accountState.third != expectedPasswordHash) return@transaction GoogleLinkResult.INVALID_PASSWORD

                val owner = connection.prepareStatement(
                    """SELECT user_id FROM auth_identities
                       WHERE provider = ? AND provider_subject = ?""".trimIndent()
                ).use { statement ->
                    statement.setString(1, PROVIDER)
                    statement.setString(2, subject)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.getLong("user_id") else null
                    }
                }
                if (owner != null) {
                    return@transaction if (owner == userId) GoogleLinkResult.ALREADY_LINKED
                    else GoogleLinkResult.IDENTITY_IN_USE
                }

                val currentGoogleIdentity = connection.prepareStatement(
                    """SELECT provider_subject FROM auth_identities
                       WHERE user_id = ? AND provider = ?""".trimIndent()
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setString(2, PROVIDER)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.getString("provider_subject") else null
                    }
                }
                if (currentGoogleIdentity != null) {
                    return@transaction if (currentGoogleIdentity == subject) GoogleLinkResult.ALREADY_LINKED
                    else GoogleLinkResult.IDENTITY_IN_USE
                }

                connection.prepareStatement(
                    """INSERT INTO auth_identities (user_id, provider, provider_subject)
                       VALUES (?, ?, ?)""".trimIndent()
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setString(2, PROVIDER)
                    statement.setString(3, subject)
                    statement.executeUpdate()
                }
                GoogleLinkResult.LINKED
            }
        }
    }

    private fun java.sql.ResultSet.toGoogleAccount(): GoogleAccount = GoogleAccount(
        userId = getLong("id"),
        fullName = getString("full_name"),
        email = getString("email"),
        role = getString("role"),
        accountStatus = getString("account_status"),
        passwordHash = getString("password_hash")
    )

    private inline fun <T> transaction(connection: Connection, block: () -> T): T {
        val previousAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val result = block()
            connection.commit()
            return result
        } catch (exception: Exception) {
            try {
                connection.rollback()
            } catch (rollbackException: Exception) {
                exception.addSuppressed(rollbackException)
            }
            throw exception
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }
}
