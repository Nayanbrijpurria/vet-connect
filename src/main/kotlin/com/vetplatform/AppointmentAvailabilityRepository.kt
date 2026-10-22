
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Time
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AppointmentAvailabilityRepository {

    suspend fun getAvailableSlots(
        vetUserId: Long,
        date: LocalDate
    ): AvailableAppointmentSlotsResponse =
        withContext(Dispatchers.IO) {

            require(!date.isBefore(LocalDate.now())) {
                "Please select today or a future date"
            }

            DatabaseFactory.withConnection { connection ->

                // Only approved veterinarians can receive bookings.
                val vet = connection.prepareStatement(
                    """
                    SELECT vp.user_id, vp.timezone
                    FROM vet_profiles vp
                    JOIN users u ON u.id = vp.user_id
                    WHERE vp.user_id = ?
                      AND u.role = 'VET'
                      AND vp.verification_status = 'APPROVED'
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, vetUserId)

                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.getLong("user_id") to
                                    ZoneId.of(rs.getString("timezone"))
                        } else {
                            null
                        }
                    }
                } ?: throw IllegalArgumentException(
                    "Veterinarian not found or not approved"
                )

                val timezone = vet.second
                val localDateTimeNow = ZonedDateTime.now(timezone)

                val slots = mutableListOf<AvailableAppointmentSlot>()

                // Do not offer appointments that have already started.
                val schedules = connection.prepareStatement(
                    """
                    SELECT start_time, end_time, slot_duration_minutes
                    FROM vet_availability
                    WHERE vet_profile_id = ?
                      AND day_of_week = ?
                      AND active = TRUE
                    ORDER BY start_time
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, vetUserId)
                    statement.setInt(2, date.dayOfWeek.value)

                    statement.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(
                                    Triple(
                                        rs.getTime("start_time").toLocalTime(),
                                        rs.getTime("end_time").toLocalTime(),
                                        rs.getInt("slot_duration_minutes")
                                    )
                                )
                            }
                        }
                    }
                }

                for ((scheduleStart, scheduleEnd, durationMinutes) in schedules) {
                    var slotStart = scheduleStart

                    while (!slotStart.plusMinutes(durationMinutes.toLong())
                            .isAfter(scheduleEnd)
                    ) {
                        val slotEnd =
                            slotStart.plusMinutes(durationMinutes.toLong())

                        val startZoned = ZonedDateTime.of(
                            date,
                            slotStart,
                            timezone
                        )

                        val endZoned = ZonedDateTime.of(
                            date,
                            slotEnd,
                            timezone
                        )

                        val startOffset = startZoned.toOffsetDateTime()
                        val endOffset = endZoned.toOffsetDateTime()

                        val isFuture = startZoned.isAfter(localDateTimeNow)

                        val hasTimeOff = connection.prepareStatement(
                            """
                            SELECT EXISTS (
                                SELECT 1
                                FROM vet_time_off
                                WHERE vet_profile_id = ?
                                  AND starts_at < ?
                                  AND ends_at > ?
                            )
                            """.trimIndent()
                        ).use { statement ->
                            statement.setLong(1, vetUserId)
                            statement.setObject(2, endOffset)
                            statement.setObject(3, startOffset)

                            statement.executeQuery().use { rs ->
                                rs.next()
                                rs.getBoolean(1)
                            }
                        }

                        val hasAppointment = connection.prepareStatement(
                            """
                            SELECT EXISTS (
                                SELECT 1
                                FROM appointments
                                WHERE vet_profile_id = (
                                    SELECT id
                                    FROM vet_profiles
                                    WHERE user_id = ?
                                )
                                  AND status IN ('PENDING', 'CONFIRMED')
                                  AND scheduled_start < ?
                                  AND scheduled_end > ?
                            )
                            """.trimIndent()
                        ).use { statement ->
                            statement.setLong(1, vetUserId)
                            statement.setObject(2, endOffset)
                            statement.setObject(3, startOffset)

                            statement.executeQuery().use { rs ->
                                rs.next()
                                rs.getBoolean(1)
                            }
                        }

                        if (isFuture && !hasTimeOff && !hasAppointment) {
                            slots.add(
                                AvailableAppointmentSlot(
                                    start = startOffset.toString(),
                                    end = endOffset.toString()
                                )
                            )
                        }

                        slotStart = slotEnd
                    }
                }

                AvailableAppointmentSlotsResponse(
                    vetUserId = vetUserId,
                    date = date.toString(),
                    timezone = timezone.id,
                    slots = slots
                )
            }
        }
}
