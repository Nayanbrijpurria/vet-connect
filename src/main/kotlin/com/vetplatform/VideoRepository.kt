package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Timestamp
import java.sql.Connection
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

data class VideoAppointment(
    val appointmentId: Long,
    val customerId: Long,
    val vetUserId: Long,
    val status: AppointmentStatus,
    val scheduledStart: Instant,
    val scheduledEnd: Instant
)

data class VideoSessionRecord(
    val id: Long,
    val appointmentId: Long,
    val roomName: String,
    val status: VideoSessionStatus,
    val scheduledStart: Instant,
    val scheduledEnd: Instant
)

internal fun isAuthorizedVideoParticipant(
    appointment: VideoAppointment,
    userId: Long,
    role: String?,
    accountActive: Boolean,
    veterinarianApproved: Boolean
): Boolean {
    if (!accountActive) return false
    return when (role) {
        "CUSTOMER" -> appointment.customerId == userId
        "VET" -> appointment.vetUserId == userId && veterinarianApproved
        else -> false
    }
}

internal fun isVideoAppointmentEligible(appointment: VideoAppointment): Boolean =
    appointment.status == AppointmentStatus.CONFIRMED

internal fun isWithinVideoJoinWindow(
    now: Instant,
    scheduledStart: Instant,
    earlyMinutes: Int,
    lateMinutes: Int
): Boolean {
    require(earlyMinutes >= 0 && lateMinutes >= 0) { "Join window values cannot be negative" }
    return !now.isBefore(scheduledStart.minusSeconds(earlyMinutes * 60L)) &&
        !now.isAfter(scheduledStart.plusSeconds(lateMinutes * 60L))
}

object VideoRepository {
    suspend fun getAppointment(appointmentId: Long): VideoAppointment? =
        withContext(Dispatchers.IO) {
            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(
                    """
                    SELECT a.id, a.customer_id, vp.user_id AS vet_user_id, a.status,
                           a.scheduled_start, a.scheduled_end
                    FROM appointments a
                    JOIN vet_profiles vp ON vp.id = a.vet_profile_id
                    WHERE a.id = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, appointmentId)
                    statement.executeQuery().use { result ->
                        if (!result.next()) null else VideoAppointment(
                            appointmentId = result.getLong("id"),
                            customerId = result.getLong("customer_id"),
                            vetUserId = result.getLong("vet_user_id"),
                            status = AppointmentStatus.valueOf(result.getString("status")),
                            scheduledStart = result.getObject("scheduled_start", OffsetDateTime::class.java).toInstant(),
                            scheduledEnd = result.getObject("scheduled_end", OffsetDateTime::class.java).toInstant()
                        )
                    }
                }
            }
        }

    suspend fun getOrCreateSession(appointment: VideoAppointment): VideoSessionRecord =
        withContext(Dispatchers.IO) {
            DatabaseFactory.withConnection { connection ->
                val existing = connection.prepareStatement(
                    "SELECT id, room_name, status, scheduled_start, scheduled_end FROM video_sessions WHERE appointment_id = ?"
                ).use { statement ->
                    statement.setLong(1, appointment.appointmentId)
                    statement.executeQuery().use { result ->
                        if (result.next()) VideoSessionRecord(
                            id = result.getLong("id"),
                            appointmentId = appointment.appointmentId,
                            roomName = result.getString("room_name"),
                            status = VideoSessionStatus.valueOf(result.getString("status")),
                            scheduledStart = result.getObject("scheduled_start", OffsetDateTime::class.java).toInstant(),
                            scheduledEnd = result.getObject("scheduled_end", OffsetDateTime::class.java).toInstant()
                        ) else null
                    }
                }
                if (existing != null) return@withConnection existing

                val roomName = "vet-${UUID.randomUUID()}"
                connection.prepareStatement(
                    """
                    INSERT INTO video_sessions (
                        appointment_id, provider, room_name, status, scheduled_start, scheduled_end
                    ) VALUES (?, 'LIVEKIT', ?, 'SCHEDULED', ?, ?)
                    ON CONFLICT (appointment_id) DO NOTHING
                    RETURNING id, room_name, status, scheduled_start, scheduled_end
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, appointment.appointmentId)
                    statement.setString(2, roomName)
                    statement.setTimestamp(3, Timestamp.from(appointment.scheduledStart))
                    statement.setTimestamp(4, Timestamp.from(appointment.scheduledEnd))
                    statement.executeQuery().use { result ->
                        if (result.next()) VideoSessionRecord(
                            id = result.getLong("id"),
                            appointmentId = appointment.appointmentId,
                            roomName = result.getString("room_name"),
                            status = VideoSessionStatus.valueOf(result.getString("status")),
                            scheduledStart = result.getObject("scheduled_start", OffsetDateTime::class.java).toInstant(),
                            scheduledEnd = result.getObject("scheduled_end", OffsetDateTime::class.java).toInstant()
                        ) else getOrCreateSessionExisting(connection, appointment.appointmentId)
                    }
                }
            }
        }

    private fun getOrCreateSessionExisting(connection: java.sql.Connection, appointmentId: Long): VideoSessionRecord =
        connection.prepareStatement(
            "SELECT id, room_name, status, scheduled_start, scheduled_end FROM video_sessions WHERE appointment_id = ?"
        ).use { statement ->
            statement.setLong(1, appointmentId)
            statement.executeQuery().use { result ->
                check(result.next()) { "Video session could not be provisioned" }
                VideoSessionRecord(
                    id = result.getLong("id"),
                    appointmentId = appointmentId,
                    roomName = result.getString("room_name"),
                    status = VideoSessionStatus.valueOf(result.getString("status")),
                    scheduledStart = result.getObject("scheduled_start", OffsetDateTime::class.java).toInstant(),
                    scheduledEnd = result.getObject("scheduled_end", OffsetDateTime::class.java).toInstant()
                )
            }
        }

    suspend fun recordParticipant(
        sessionId: Long,
        userId: Long,
        role: String,
        identity: String
    ) = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO video_session_participants (
                    video_session_id, user_id, participant_role,
                    participant_identity
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT (video_session_id, participant_identity)
                DO UPDATE SET updated_at = CURRENT_TIMESTAMP
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, sessionId)
                statement.setLong(2, userId)
                statement.setString(3, role)
                statement.setString(4, identity)
                statement.executeUpdate()
            }
        }
    }

    suspend fun getSession(appointmentId: Long): VideoSessionRecord? =
        withContext(Dispatchers.IO) {
            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(
                    """
                    SELECT id, room_name, status, scheduled_start, scheduled_end
                    FROM video_sessions
                    WHERE appointment_id = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, appointmentId)
                    statement.executeQuery().use { result ->
                        if (!result.next()) null else VideoSessionRecord(
                            id = result.getLong("id"),
                            appointmentId = appointmentId,
                            roomName = result.getString("room_name"),
                            status = VideoSessionStatus.valueOf(result.getString("status")),
                            scheduledStart = result.getObject("scheduled_start", OffsetDateTime::class.java).toInstant(),
                            scheduledEnd = result.getObject("scheduled_end", OffsetDateTime::class.java).toInstant()
                        )
                    }
                }
            }
        }

    suspend fun endSession(appointmentId: Long) = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE video_sessions
                SET status = 'ENDED',
                    actual_end = COALESCE(actual_end, CURRENT_TIMESTAMP),
                    updated_at = CURRENT_TIMESTAMP
                WHERE appointment_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, appointmentId)
                statement.executeUpdate()
            }
        }
    }

    suspend fun processWebhookEvent(
        eventId: String,
        eventType: String,
        roomName: String?,
        participantIdentity: String?,
        occurredAt: Instant
    ): Boolean = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            withJdbcTransaction(connection) { transaction ->
                val inserted = transaction.prepareStatement(
                    """
                    INSERT INTO video_webhook_events
                        (event_id, event_type, room_name, participant_identity)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (event_id) DO NOTHING
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, eventId)
                    statement.setString(2, eventType)
                    statement.setString(3, roomName)
                    statement.setString(4, participantIdentity)
                    statement.executeUpdate() == 1
                }

                if (inserted && roomName != null) {
                    when (eventType) {
                        "room_started" -> transaction.prepareStatement(
                        """
                        UPDATE video_sessions
                        SET status = 'ACTIVE',
                            actual_start = COALESCE(actual_start, ?),
                            updated_at = CURRENT_TIMESTAMP
                        WHERE room_name = ? AND status NOT IN ('ENDED', 'EXPIRED')
                        """.trimIndent()
                        ).use { statement ->
                            statement.setTimestamp(1, Timestamp.from(occurredAt))
                            statement.setString(2, roomName)
                            statement.executeUpdate()
                        }
                        "room_finished" -> transaction.prepareStatement(
                        """
                        UPDATE video_sessions
                        SET status = 'ENDED',
                            actual_end = COALESCE(actual_end, ?),
                            updated_at = CURRENT_TIMESTAMP
                        WHERE room_name = ? AND status <> 'EXPIRED'
                        """.trimIndent()
                        ).use { statement ->
                            statement.setTimestamp(1, Timestamp.from(occurredAt))
                            statement.setString(2, roomName)
                            statement.executeUpdate()
                        }
                        "participant_joined", "participant_left", "participant_connection_aborted" -> {
                            if (participantIdentity != null) {
                                val joined = eventType == "participant_joined"
                                transaction.prepareStatement(
                                """
                                UPDATE video_session_participants p
                                SET join_timestamp = CASE
                                        WHEN ? AND (p.leave_timestamp IS NULL OR ? >= p.leave_timestamp)
                                        THEN GREATEST(COALESCE(p.join_timestamp, ?), ?)
                                        ELSE p.join_timestamp
                                    END,
                                    leave_timestamp = CASE
                                        WHEN NOT ? AND (p.join_timestamp IS NULL OR ? >= p.join_timestamp)
                                        THEN GREATEST(COALESCE(p.leave_timestamp, ?), ?)
                                        WHEN ? AND p.leave_timestamp IS NOT NULL AND ? >= p.leave_timestamp
                                        THEN NULL
                                        ELSE p.leave_timestamp
                                    END,
                                    updated_at = CURRENT_TIMESTAMP
                                FROM video_sessions s
                                WHERE s.id = p.video_session_id
                                  AND s.room_name = ?
                                  AND p.participant_identity = ?
                                """.trimIndent()
                                ).use { statement ->
                                    statement.setBoolean(1, joined)
                                    statement.setTimestamp(2, Timestamp.from(occurredAt))
                                    statement.setTimestamp(3, Timestamp.from(occurredAt))
                                    statement.setTimestamp(4, Timestamp.from(occurredAt))
                                    statement.setBoolean(5, joined)
                                    statement.setTimestamp(6, Timestamp.from(occurredAt))
                                    statement.setTimestamp(7, Timestamp.from(occurredAt))
                                    statement.setTimestamp(8, Timestamp.from(occurredAt))
                                    statement.setBoolean(9, joined)
                                    statement.setTimestamp(10, Timestamp.from(occurredAt))
                                    statement.setString(11, roomName)
                                    statement.setString(12, participantIdentity)
                                    statement.executeUpdate()
                                }
                            }
                        }
                    }
                }
                inserted
            }
        }
    }
}

internal fun <T> withJdbcTransaction(
    connection: Connection,
    operation: (Connection) -> T
): T {
    val originalAutoCommit = connection.autoCommit
    try {
        connection.autoCommit = false
        val result = operation(connection)
        connection.commit()
        return result
    } catch (exception: Throwable) {
        try {
            connection.rollback()
        } catch (rollbackException: Throwable) {
            exception.addSuppressed(rollbackException)
        }
        throw exception
    } finally {
        connection.autoCommit = originalAutoCommit
    }
}
