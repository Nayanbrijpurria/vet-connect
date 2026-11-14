package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class RefreshSessionUser(
    val userId: Long,
    val email: String,
    val role: String,
    val accountStatus: String
)

enum class RefreshRotationStatus { ROTATED, INVALID, EXPIRED, REUSED, INACTIVE }

data class RefreshRotationResult(
    val status: RefreshRotationStatus,
    val user: RefreshSessionUser? = null
)

object RefreshTokenRepository {

    suspend fun createSession(
        userId: Long,
        tokenHash: String,
        expiresAt: Instant
    ): UUID = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            transaction(connection) {
                val sessionId = UUID.randomUUID()
                val familyId = UUID.randomUUID()
                connection.prepareStatement(
                    """INSERT INTO refresh_token_sessions
                       (session_id, family_id, user_id, token_hash, expires_at)
                       SELECT ?, ?, id, ?, ? FROM users
                       WHERE id = ? AND account_status = 'ACTIVE'""".trimIndent()
                ).use { statement ->
                    statement.setObject(1, sessionId)
                    statement.setObject(2, familyId)
                    statement.setString(3, tokenHash)
                    statement.setTimestamp(4, Timestamp.from(expiresAt))
                    statement.setLong(5, userId)
                    check(statement.executeUpdate() == 1) { "Active account required to create a session" }
                }
                sessionId
            }
        }
    }

    suspend fun rotate(
        oldTokenHash: String,
        newTokenHash: String,
        newExpiresAt: Instant,
        now: Instant = Instant.now()
    ): RefreshRotationResult = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            transaction(connection) {
                val session = connection.prepareStatement(
                    """SELECT s.session_id, s.family_id, s.user_id, s.expires_at,
                              s.revoked_at, u.email, u.role, u.account_status
                       FROM refresh_token_sessions s
                       JOIN users u ON u.id = s.user_id
                       WHERE s.token_hash = ?
                       FOR UPDATE OF s, u""".trimIndent()
                ).use { statement ->
                    statement.setString(1, oldTokenHash)
                    statement.executeQuery().use { result ->
                        if (result.next()) SessionRow(
                            sessionId = result.getObject("session_id", UUID::class.java),
                            familyId = result.getObject("family_id", UUID::class.java),
                            user = RefreshSessionUser(
                                userId = result.getLong("user_id"),
                                email = result.getString("email"),
                                role = result.getString("role"),
                                accountStatus = result.getString("account_status")
                            ),
                            expiresAt = result.getTimestamp("expires_at").toInstant(),
                            revoked = result.getTimestamp("revoked_at") != null
                        ) else null
                    }
                } ?: return@transaction RefreshRotationResult(RefreshRotationStatus.INVALID)

                if (session.revoked) {
                    revokeFamily(connection, session.familyId, now)
                    return@transaction RefreshRotationResult(RefreshRotationStatus.REUSED)
                }
                if (!session.expiresAt.isAfter(now)) {
                    revokeSession(connection, session.sessionId, now)
                    return@transaction RefreshRotationResult(RefreshRotationStatus.EXPIRED)
                }
                if (session.user.accountStatus != "ACTIVE") {
                    revokeUserSessions(connection, session.user.userId, now)
                    return@transaction RefreshRotationResult(RefreshRotationStatus.INACTIVE)
                }

                val nextId = UUID.randomUUID()
                connection.prepareStatement(
                    """INSERT INTO refresh_token_sessions
                       (session_id, family_id, user_id, token_hash, expires_at)
                       VALUES (?, ?, ?, ?, ?)""".trimIndent()
                ).use { statement ->
                    statement.setObject(1, nextId)
                    statement.setObject(2, session.familyId)
                    statement.setLong(3, session.user.userId)
                    statement.setString(4, newTokenHash)
                    statement.setTimestamp(5, Timestamp.from(newExpiresAt))
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """UPDATE refresh_token_sessions
                       SET revoked_at = ?, replaced_by_session_id = ?
                       WHERE session_id = ? AND revoked_at IS NULL""".trimIndent()
                ).use { statement ->
                    statement.setTimestamp(1, Timestamp.from(now))
                    statement.setObject(2, nextId)
                    statement.setObject(3, session.sessionId)
                    check(statement.executeUpdate() == 1) { "Refresh session was already consumed" }
                }
                RefreshRotationResult(RefreshRotationStatus.ROTATED, session.user)
            }
        }
    }

    suspend fun revokeByTokenHash(tokenHash: String): Boolean = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                "UPDATE refresh_token_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL"
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.executeUpdate() > 0
            }
        }
    }

    suspend fun revokeFamilyByTokenHash(tokenHash: String): Boolean = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            transaction(connection) {
                val familyId = connection.prepareStatement(
                    "SELECT family_id FROM refresh_token_sessions WHERE token_hash = ? FOR UPDATE"
                ).use { statement ->
                    statement.setString(1, tokenHash)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.getObject("family_id", UUID::class.java) else null
                    }
                } ?: return@transaction false
                revokeFamily(connection, familyId, Instant.now())
                true
            }
        }
    }

    suspend fun revokeAllForUser(userId: Long): Int = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                "UPDATE refresh_token_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL"
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeUpdate()
            }
        }
    }

    suspend fun expireOldSessions(): Int = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                "UPDATE refresh_token_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE expires_at <= CURRENT_TIMESTAMP AND revoked_at IS NULL"
            ).use { it.executeUpdate() }
        }
    }

    private fun revokeSession(connection: Connection, sessionId: UUID, now: Instant) {
        connection.prepareStatement(
            "UPDATE refresh_token_sessions SET revoked_at = ? WHERE session_id = ? AND revoked_at IS NULL"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(now))
            statement.setObject(2, sessionId)
            statement.executeUpdate()
        }
    }

    private fun revokeFamily(connection: Connection, familyId: UUID, now: Instant) {
        connection.prepareStatement(
            "UPDATE refresh_token_sessions SET revoked_at = ? WHERE family_id = ? AND revoked_at IS NULL"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(now))
            statement.setObject(2, familyId)
            statement.executeUpdate()
        }
    }

    private fun revokeUserSessions(connection: Connection, userId: Long, now: Instant) {
        connection.prepareStatement(
            "UPDATE refresh_token_sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(now))
            statement.setLong(2, userId)
            statement.executeUpdate()
        }
    }

    private data class SessionRow(
        val sessionId: UUID,
        val familyId: UUID,
        val user: RefreshSessionUser,
        val expiresAt: Instant,
        val revoked: Boolean
    )

    private inline fun <T> transaction(connection: Connection, block: () -> T): T {
        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val value = block()
            connection.commit()
            return value
        } catch (exception: Exception) {
            try {
                connection.rollback()
            } catch (rollbackException: Exception) {
                exception.addSuppressed(rollbackException)
            }
            throw exception
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }
}
