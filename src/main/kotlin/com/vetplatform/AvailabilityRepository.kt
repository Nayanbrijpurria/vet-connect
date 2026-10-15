
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.time.LocalTime

object AvailabilityRepository {

    private fun mapAvailability(rs: ResultSet): AvailabilityResponse {
        return AvailabilityResponse(
            id = rs.getLong("id"),
            vetUserId = rs.getLong("vet_profile_id"),
            dayOfWeek = rs.getInt("day_of_week"),
            startTime = rs.getTime("start_time").toLocalTime().toString(),
            endTime = rs.getTime("end_time").toLocalTime().toString(),
            slotDurationMinutes = rs.getInt("slot_duration_minutes"),
            active = rs.getBoolean("active")
        )
    }

    suspend fun createAvailability(
        vetUserId: Long,
        request: CreateAvailabilityRequest
    ): AvailabilityResponse = withContext(Dispatchers.IO) {

        require(request.dayOfWeek in 1..7) {
            "dayOfWeek must be between 1 (Monday) and 7 (Sunday)"
        }

        val startTime = try {
            LocalTime.parse(request.startTime)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "startTime must use HH:mm format, for example 09:30"
            )
        }

        val endTime = try {
            LocalTime.parse(request.endTime)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "endTime must use HH:mm format, for example 17:00"
            )
        }

        require(endTime.isAfter(startTime)) {
            "endTime must be later than startTime"
        }

        require(request.slotDurationMinutes in 10..240) {
            "slotDurationMinutes must be between 10 and 240"
        }

        DatabaseFactory.withConnection { connection ->
            val originalAutoCommit = connection.autoCommit

            try {
                connection.autoCommit = false

                // Lock this veterinarian's profile while checking schedules.
                val profileExists = connection.prepareStatement(
                    """
                    SELECT user_id
                    FROM vet_profiles
                    WHERE user_id = ?
                    FOR UPDATE
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, vetUserId)
                    statement.executeQuery().use { rs -> rs.next() }
                }

                require(profileExists) {
                    "Veterinarian profile not found"
                }

                // Reject overlapping active schedules on the same weekday.
                val overlapExists = connection.prepareStatement(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM vet_availability
                        WHERE vet_profile_id = ?
                          AND day_of_week = ?
                          AND active = TRUE
                          AND start_time < ?
                          AND end_time > ?
                    )
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, vetUserId)
                    statement.setInt(2, request.dayOfWeek)
                    statement.setTime(3, java.sql.Time.valueOf(endTime))
                    statement.setTime(4, java.sql.Time.valueOf(startTime))

                    statement.executeQuery().use { rs ->
                        rs.next()
                        rs.getBoolean(1)
                    }
                }

                require(!overlapExists) {
                    "This schedule overlaps an existing active schedule"
                }

                val availability = connection.prepareStatement(
                    """
                    INSERT INTO vet_availability (
                        vet_profile_id,
                        day_of_week,
                        start_time,
                        end_time,
                        slot_duration_minutes
                    )
                    VALUES (?, ?, ?, ?, ?)
                    RETURNING id, vet_profile_id, day_of_week,
                              start_time, end_time,
                              slot_duration_minutes, active
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, vetUserId)
                    statement.setInt(2, request.dayOfWeek)
                    statement.setTime(3, java.sql.Time.valueOf(startTime))
                    statement.setTime(4, java.sql.Time.valueOf(endTime))
                    statement.setInt(5, request.slotDurationMinutes)

                    statement.executeQuery().use { rs ->
                        check(rs.next()) {
                            "Failed to create availability"
                        }
                        mapAvailability(rs)
                    }
                }

                connection.commit()
                availability
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        }
    }

    suspend fun getAvailability(
        vetUserId: Long,
        includeInactive: Boolean = false
    ): AvailabilityListResponse = withContext(Dispatchers.IO) {

        val availability = DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, vet_profile_id, day_of_week,
                       start_time, end_time,
                       slot_duration_minutes, active
                FROM vet_availability
                WHERE vet_profile_id = ?
                  AND (? = TRUE OR active = TRUE)
                ORDER BY day_of_week, start_time
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, vetUserId)
                statement.setBoolean(2, includeInactive)

                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(mapAvailability(rs))
                        }
                    }
                }
            }
        }

        AvailabilityListResponse(availability)
    }

    suspend fun deactivateAvailability(
        vetUserId: Long,
        availabilityId: Long
    ): Boolean = withContext(Dispatchers.IO) {

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE vet_availability
                SET active = FALSE
                WHERE id = ?
                  AND vet_profile_id = ?
                  AND active = TRUE
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, availabilityId)
                statement.setLong(2, vetUserId)
                statement.executeUpdate() == 1
            }
        }
    }
}
