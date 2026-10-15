
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.time.OffsetDateTime

object TimeOffRepository {

    private fun mapTimeOff(rs: ResultSet): TimeOffResponse {
        return TimeOffResponse(
            id = rs.getLong("id"),
            vetUserId = rs.getLong("vet_profile_id"),
            startsAt = rs.getObject(
                "starts_at",
                OffsetDateTime::class.java
            ).toString(),
            endsAt = rs.getObject(
                "ends_at",
                OffsetDateTime::class.java
            ).toString(),
            reason = rs.getString("reason")
        )
    }

    suspend fun createTimeOff(
        vetUserId: Long,
        request: CreateTimeOffRequest
    ): TimeOffResponse = withContext(Dispatchers.IO) {

        val start = try {
            OffsetDateTime.parse(request.startsAt)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "startsAt must be an ISO-8601 timestamp with timezone"
            )
        }

        val end = try {
            OffsetDateTime.parse(request.endsAt)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "endsAt must be an ISO-8601 timestamp with timezone"
            )
        }

        require(end.isAfter(start)) {
            "endsAt must be later than startsAt"
        }

        require(start.isAfter(OffsetDateTime.now())) {
            "Time off must start in the future"
        }

        require(request.reason == null || request.reason.length <= 250) {
            "Reason must not exceed 250 characters"
        }

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO vet_time_off (
                    vet_profile_id,
                    starts_at,
                    ends_at,
                    reason
                )
                SELECT user_id, ?, ?, ?
                FROM vet_profiles
                WHERE user_id = ?
                RETURNING id, vet_profile_id, starts_at, ends_at, reason
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, start)
                statement.setObject(2, end)
                statement.setString(3, request.reason)
                statement.setLong(4, vetUserId)

                statement.executeQuery().use { rs ->
                    if (!rs.next()) {
                        throw IllegalArgumentException(
                            "Veterinarian profile not found"
                        )
                    }

                    mapTimeOff(rs)
                }
            }
        }
    }

    suspend fun getTimeOff(vetUserId: Long): TimeOffListResponse =
        withContext(Dispatchers.IO) {

            val records = DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(
                    """
                    SELECT id, vet_profile_id, starts_at, ends_at, reason
                    FROM vet_time_off
                    WHERE vet_profile_id = ?
                    ORDER BY starts_at
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, vetUserId)

                    statement.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(mapTimeOff(rs))
                            }
                        }
                    }
                }
            }

            TimeOffListResponse(records)
        }

    suspend fun deleteTimeOff(
        vetUserId: Long,
        timeOffId: Long
    ): Boolean = withContext(Dispatchers.IO) {

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                DELETE FROM vet_time_off
                WHERE id = ? AND vet_profile_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, timeOffId)
                statement.setLong(2, vetUserId)
                statement.executeUpdate() == 1
            }
        }
    }
}
